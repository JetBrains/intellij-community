// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteSdkAdditionalData;
import com.intellij.remote.RemoteSdkException;
import com.intellij.remote.RemoteSdkFactoryImpl;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public abstract class CreateRemoteSdkDialog<T extends RemoteSdkAdditionalData<?>> extends DialogWrapper implements RemoteSdkEditorContainer {
  private static final Logger LOG = Logger.getInstance(CreateRemoteSdkDialog.class);
  @Nullable
  protected final Project myProject;
  private CreateRemoteSdkForm<T> myInterpreterForm;
  private Sdk mySdk;
  protected final NotNullLazyValue<RemoteSdkFactoryImpl<T>> mySdkFactoryProvider = NotNullLazyValue.atomicLazy(this::createRemoteSdkFactory);
  @Nullable
  private T myOriginalData;
  protected final Collection<Sdk> myExistingSdks;

  public CreateRemoteSdkDialog(@Nullable final Project project, Collection<Sdk> existingSdks) {
    super(project, true);
    myProject = project == null || !project.isDefault() ? project : null;
    myExistingSdks = existingSdks;
  }

  public CreateRemoteSdkDialog(Component parentComponent, Collection<Sdk> existingSdks) {
    super(parentComponent, true);
    myProject = null;
    myExistingSdks = existingSdks;
  }

  @NotNull
  protected abstract RemoteSdkFactoryImpl<T> createRemoteSdkFactory();

  protected RemoteSdkFactoryImpl<T> getSdkFactory() {
    return mySdkFactoryProvider.getValue();
  }

  @NotNull
  private CreateRemoteSdkForm<T> getInterpreterForm() {
    if (myInterpreterForm == null) {
      myInterpreterForm = createRemoteSdkForm();
    }
    return myInterpreterForm;
  }

  @NotNull
  protected abstract CreateRemoteSdkForm<T> createRemoteSdkForm();

  public final void onValidationPress() {
    initValidation();
  }

  @Override
  public void updateSize() {
    pack();
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel result = new JPanel(new BorderLayout());
    result.add(getInterpreterForm(), BorderLayout.CENTER);
    return result;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return getInterpreterForm().getPreferredFocusedComponent();
  }

  @NotNull
  public final Sdk getSdk() {
    assert mySdk != null;
    assert mySdk.getSdkAdditionalData() instanceof RemoteSdkAdditionalData;
    return mySdk;
  }

  protected void initSdk(@NotNull final Sdk sdk) throws RemoteSdkException {
    getSdkFactory().initSdk(sdk, myProject, getContentPane());
  }

  protected abstract boolean isModified(@NotNull T oldData, @NotNull T newData);

  @NotNull
  private Sdk createSdk(T remoteSdkData) throws RemoteSdkException {
    return createRemoteSdk(remoteSdkData);
  }

  private Sdk createRemoteSdk(T data) throws RemoteSdkException {
    return getSdkFactory().createRemoteSdk(myProject, data, getInterpreterForm().getSdkName(), myExistingSdks);
  }


  @Nullable
  private Sdk saveUnfinished() {
    final T data;
    try {
      data = getInterpreterForm().createSdkData();
      return getSdkFactory().createUnfinished(data, myExistingSdks);
    }
    catch (RemoteSdkException e) {
      LOG.debug(e);
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    String validation = validateInterpreterForm();
    if (validation != null) {
      onCreateFail(validation);
      return;
    }

    T remoteSdkData;
    try {
      remoteSdkData = getInterpreterForm().createSdkData();
    }
    catch (ProcessCanceledException ignored) {
      return;
    }
    catch (RemoteSdkException e) {
      LOG.warn("Failed to create SDK data", e);
      onCreateFail(e.getMessage());
      return;
    }

    if (!validateRemoteSdkData(remoteSdkData)) {
      LOG.info("Validation of SDK Data failed");
      return;
    }

    try {
      mySdk = createSdk(remoteSdkData);
      SdkAdditionalData newData = mySdk.getSdkAdditionalData();
      assert newData instanceof RemoteSdkAdditionalData;

      //noinspection unchecked
      if (((RemoteSdkAdditionalData<?>)newData).isValid() &&
          (myOriginalData == null || !myOriginalData.isValid() ||
           (myOriginalData.getClass().isInstance(newData) && isModified(myOriginalData, (T)newData)))
      ) {
        // we initialize sdk only if it is valid
        initSdk(mySdk);
      }
    }
    catch (RemoteSdkException e) {
      mySdk = null;
      if (!ExceptionUtil.causedBy(e, InterruptedException.class)) {
        LOG.debug("Failed to create remote SDK", e);
        onCreateFail(e.getMessage());
      }
      return;
    }
    ApplicationManager.getApplication().invokeAndWait(() -> super.doOKAction());
  }

  protected boolean validateRemoteSdkData(T data) {
    for (Sdk sdk : myExistingSdks) {
      if (StringUtil.equals(sdk.getHomePath(), getSdkFactory().generateSdkHomePath(data))) {
        validationFailed(IdeBundle.message("dialog.message.there.already.same.interpreter", sdk.getName()), false);
        return false;
      }
    }
    return true;
  }

  private void onCreateFail(@NlsContexts.DialogMessage String validation) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      final boolean saveAnyway = validationFailed(validation, getSdkFactory().canSaveUnfinished());
      if (saveAnyway) {
        mySdk = saveUnfinished();
        if (mySdk != null) {
          super.doOKAction();
        }
      }
    });
  }

  public void setSdkName(String name) {
    if (name != null && !name.startsWith(getSdkFactory().getDefaultUnfinishedName())) {
      getInterpreterForm().setSdkName(name);
    }
  }

  protected boolean validationFailed(@NlsContexts.DialogMessage String validation, boolean askSaveUnfinished) {
    if (StringUtil.isEmpty(validation)) {
      validation = IdeBundle.message("dialog.message.communication.error");
    }
    if (askSaveUnfinished) {
      if (Messages
            .showOkCancelDialog(validation, IdeBundle.message("dialog.title.can.t.create.0.sdk", getSdkFactory().sdkName()),
                                IdeBundle.message("button.save.anyway"),
                                IdeBundle.message("button.continue.editing"),
                                Messages.getWarningIcon()) ==
          Messages.OK) {
        return true;
      }
    }
    else {
      Messages.showErrorDialog(validation, IdeBundle.message("dialog.title.can.t.create.0.sdk", getSdkFactory().sdkName()));
    }
    return false;
  }

  @NlsContexts.DialogMessage
  @Nullable
  private String validateInterpreterForm() {
    return getInterpreterForm().validateFinal();
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    return getInterpreterForm().validateRemoteInterpreter();
  }

  public void setEditing(final @NotNull T originalData) {
    getInterpreterForm().init(originalData);
    myOriginalData = originalData;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @TestOnly
  public CreateRemoteSdkForm<T> getRemoteSdkForm() {
    return myInterpreterForm;
  }
}
