// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Function;
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogRefs;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcsUtil.VcsImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class GoToHashOrRefPopup {
  private static final Logger LOG = Logger.getInstance(GoToHashOrRefPopup.class);

  @NotNull private final TextFieldWithProgress myTextField;
  @NotNull private final Function<? super String, ? extends Future> myOnSelectedHash;
  @NotNull private final Function<? super VcsRef, ? extends Future> myOnSelectedRef;
  @NotNull private final JBPopup myPopup;
  @Nullable private Future myFuture;
  @Nullable private VcsRef mySelectedRef;

  public GoToHashOrRefPopup(@NotNull Project project,
                            @NotNull VcsLogRefs variants,
                            @NotNull Collection<? extends VirtualFile> roots,
                            @NotNull Function<? super String, ? extends Future> onSelectedHash,
                            @NotNull Function<? super VcsRef, ? extends Future> onSelectedRef,
                            @NotNull VcsLogColorManager colorManager,
                            @NotNull Comparator<? super VcsRef> comparator) {
    myOnSelectedHash = onSelectedHash;
    myOnSelectedRef = onSelectedRef;
    VcsRefDescriptor vcsRefDescriptor = new VcsRefDescriptor(project, colorManager, comparator, roots);
    VcsRefCompletionProvider completionProvider = new VcsRefCompletionProvider(variants, roots, vcsRefDescriptor);
    myTextField =
      new TextFieldWithProgress(project, completionProvider) {
        @Override
        public void onOk() {
          if (myFuture == null) {
            final Future future = ((mySelectedRef == null || (!mySelectedRef.getName().equals(getText().trim())))
                                   ? myOnSelectedHash.fun(getText().trim())
                                   : myOnSelectedRef.fun(mySelectedRef));
            myFuture = future;
            showProgress();
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
              try {
                future.get();
                okPopup();
              }
              catch (CancellationException | InterruptedException ex) {
                cancelPopup();
              }
              catch (ExecutionException ex) {
                LOG.error(ex);
                cancelPopup();
              }
            });
          }
        }
      };
    myTextField.setAlignmentX(Component.LEFT_ALIGNMENT);
    myTextField.setBorder(JBUI.Borders.empty(3));

    JBLabel label = new JBLabel(VcsLogBundle.message("vcs.log.go.to.hash.popup.label"));
    label.setFont(StartupUiUtil.getLabelFont().deriveFont(Font.BOLD));
    label.setAlignmentX(Component.LEFT_ALIGNMENT);

    JPanel panel = new JPanel();
    BoxLayout layout = new BoxLayout(panel, BoxLayout.PAGE_AXIS);
    panel.setLayout(layout);
    panel.add(label);
    panel.add(myTextField);
    panel.setBorder(JBUI.Borders.empty(2));

    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, myTextField.getPreferableFocusComponent())
      .setCancelOnClickOutside(true).setCancelOnWindowDeactivation(true).setCancelKeyEnabled(true).setRequestFocus(true).createPopup();
    myPopup.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        if (!event.isOk()) {
          if (myFuture != null) {
            myFuture.cancel(true);
          }
        }
        myFuture = null;
        myTextField.hideProgress();
      }
    });
  }

  private void cancelPopup() {
    ApplicationManager.getApplication().invokeLater(() -> myPopup.cancel());
  }

  private void okPopup() {
    ApplicationManager.getApplication().invokeLater(() -> myPopup.closeOk(null));
  }

  public void show(@NotNull JComponent anchor) {
    myPopup.showInCenterOf(anchor);
  }

  private final class VcsRefDescriptor extends DefaultTextCompletionValueDescriptor<VcsRef> {
    @NotNull private final Project myProject;
    @NotNull private final VcsLogColorManager myColorManager;
    @NotNull private final Comparator<? super VcsRef> myReferenceComparator;
    @NotNull private final Map<VirtualFile, String> myCachedRootNames = new HashMap<>();

    private VcsRefDescriptor(@NotNull Project project,
                             @NotNull VcsLogColorManager manager,
                             @NotNull Comparator<? super VcsRef> comparator,
                             @NotNull Collection<? extends VirtualFile> roots) {
      myProject = project;
      myColorManager = manager;
      myReferenceComparator = comparator;

      for (VirtualFile root : roots) {
        String text = VcsImplUtil.getShortVcsRootName(myProject, root);
        myCachedRootNames.put(root, text);
      }
    }

    @NotNull
    @Override
    public LookupElementBuilder createLookupBuilder(@NotNull VcsRef item) {
      LookupElementBuilder lookupBuilder = super.createLookupBuilder(item);
      if (myColorManager.hasMultiplePaths()) {
        ColorIcon icon = JBUIScale.scaleIcon(new ColorIcon(15, VcsLogGraphTable.getRootBackgroundColor(item.getRoot(), myColorManager)));
        lookupBuilder = lookupBuilder.withTypeText(getTypeText(item), icon, true).withTypeIconRightAligned(true);
      }
      return lookupBuilder;
    }

    @NotNull
    @Override
    public String getLookupString(@NotNull VcsRef item) {
      return item.getName();
    }

    @Nullable
    @Override
    protected String getTailText(@NotNull VcsRef item) {
      if (!myColorManager.hasMultiplePaths()) return null;
      return "";
    }

    @Nullable
    @Override
    protected String getTypeText(@NotNull VcsRef item) {
      if (!myColorManager.hasMultiplePaths()) return null;
      String text = myCachedRootNames.get(item.getRoot());
      if (text == null) {
        return VcsImplUtil.getShortVcsRootName(myProject, item.getRoot());
      }
      return text;
    }

    @Override
    public int compare(VcsRef item1, VcsRef item2) {
      return myReferenceComparator.compare(item1, item2);
    }

    @Nullable
    @Override
    protected InsertHandler<LookupElement> createInsertHandler(@NotNull VcsRef item) {
      return (context, item1) -> {
        mySelectedRef = (VcsRef)item1.getObject();
        ApplicationManager.getApplication().invokeLater(() -> {
          // handleInsert is called in the middle of some other code that works with editor
          // (see CodeCompletionHandlerBase.insertItem)
          // for example, scrolls editor
          // problem is that in onOk we make text field not editable
          // by some reason this is done by disposing its editor and creating a new one
          // so editor gets disposed here and CodeCompletionHandlerBase can not finish doing whatever it is doing with it
          // I counter this by invoking onOk in invokeLater
          myTextField.onOk();
        });
      };
    }
  }
}
