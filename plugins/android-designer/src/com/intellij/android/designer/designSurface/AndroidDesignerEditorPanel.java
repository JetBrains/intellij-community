/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.designSurface;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.resources.configuration.*;
import com.android.sdklib.IAndroidTarget;
import com.intellij.android.designer.actions.ProfileAction;
import com.intellij.android.designer.componentTree.AndroidTreeDecorator;
import com.intellij.android.designer.inspection.ErrorAnalyzer;
import com.intellij.android.designer.model.*;
import com.intellij.android.designer.profile.ProfileManager;
import com.intellij.designer.DesignerEditor;
import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.selection.NonResizeSelectionDecorator;
import com.intellij.designer.designSurface.tools.ComponentCreationFactory;
import com.intellij.designer.designSurface.tools.ComponentPasteFactory;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadVisualComponent;
import com.intellij.designer.model.WrapInProvider;
import com.intellij.designer.palette.DefaultPaletteItem;
import com.intellij.designer.palette.PaletteGroup;
import com.intellij.designer.palette.PaletteItem;
import com.intellij.designer.palette.PaletteToolWindowManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Alarm;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.refactoring.AndroidExtractAsIncludeAction;
import org.jetbrains.android.refactoring.AndroidExtractStyleAction;
import org.jetbrains.android.refactoring.AndroidInlineIncludeAction;
import org.jetbrains.android.refactoring.AndroidInlineStyleReferenceAction;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.uipreview.*;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidSdkNotConfiguredException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class AndroidDesignerEditorPanel extends DesignerEditorPanel {
  private final TreeComponentDecorator myTreeDecorator = new AndroidTreeDecorator();
  private final XmlFile myXmlFile;
  private final ExternalPSIChangeListener myPSIChangeListener;
  private final ProfileAction myProfileAction;
  private final Alarm mySessionAlarm = new Alarm();
  private FolderConfiguration myLastRenderedConfiguration;
  private IAndroidTarget myLastTarget;
  private volatile RenderSession mySession;
  private boolean myParseTime;
  private int myProfileLastVersion;
  private WrapInProvider myWrapInProvider;

  public AndroidDesignerEditorPanel(@NotNull DesignerEditor editor,
                                    @NotNull Project project,
                                    @NotNull Module module,
                                    @NotNull VirtualFile file) {
    super(editor, project, module, file);

    myXmlFile = (XmlFile)ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        return PsiManager.getInstance(getProject()).findFile(myFile);
      }
    });
    myPSIChangeListener = new ExternalPSIChangeListener(this, myXmlFile, 100, new Runnable() {
      @Override
      public void run() {
        reparseFile();
      }
    });

    showProgress("Loading configuration...");
    myProfileAction = new ProfileAction(this, new Runnable() {
      @Override
      public void run() {
        if (isProjectClosed()) {
          return;
        }
        myPSIChangeListener.setInitialize();
        myActionPanel.update();
        if (myRootComponent == null || !Comparing.equal(myProfileAction.getProfileManager().getSelectedTarget(), myLastTarget)) {
          myPSIChangeListener.activate();
          myPSIChangeListener.addRequest();
        }
        else if (myProfileLastVersion != myProfileAction.getVersion() ||
                 !ProfileManager.isAndroidSdk(myProfileAction.getCurrentSdk())) {
          myPSIChangeListener.addRequest(new Runnable() {
            @Override
            public void run() {
              updateRenderer(true);
            }
          });
        }
      }
    });

    myActionPanel.getPopupGroup().addSeparator();
    myActionPanel.getPopupGroup().add(buildRefactorActionGroup());

    AnAction gotoDeclaration = new AnAction("Go To Declaration") {
      @Override
      public void update(AnActionEvent e) {
        EditableArea area = e.getData(EditableArea.DATA_KEY);
        e.getPresentation().setEnabled(area != null && area.getSelection().size() == 1);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        EditableArea area = e.getData(EditableArea.DATA_KEY);
        RadViewComponent component = (RadViewComponent)area.getSelection().get(0);
        PsiNavigateUtil.navigate(component.getTag());
      }
    };
    myActionPanel.registerAction(gotoDeclaration, IdeActions.ACTION_GOTO_DECLARATION);
    myActionPanel.getPopupGroup().add(gotoDeclaration);
  }

  @NotNull
  private static ActionGroup buildRefactorActionGroup() {
    final DefaultActionGroup group = new DefaultActionGroup("_Refactor", true);
    final ActionManager manager = ActionManager.getInstance();

    AnAction action = manager.getAction(AndroidExtractStyleAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("_Extract Style...", action));

    action = manager.getAction(AndroidInlineStyleReferenceAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("_Inline Style...", action));

    action = manager.getAction(AndroidExtractAsIncludeAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("E_xtract Layout...", action));

    action = manager.getAction(AndroidInlineIncludeAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("I_nline Layout...", action));
    return group;
  }

  private void reparseFile() {
    try {
      storeState();
      showDesignerCard();

      parseFile(new Runnable() {
        @Override
        public void run() {
          showDesignerCard();
          myLayeredPane.revalidate();
          restoreState();
        }
      });
    }
    catch (RuntimeException e) {
      myPSIChangeListener.clear();
      showError("Parsing error", e.getCause() == null ? e : e.getCause());
    }
  }

  private void parseFile(final Runnable runnable) {
    myParseTime = true;

    final ModelParser parser = new ModelParser(getProject(), myXmlFile);

    createRenderer(parser.getLayoutXmlText(), new MyThrowable(), new ThrowableConsumer<RenderSession, Throwable>() {
      @Override
      public void consume(RenderSession session) throws Throwable {
        RootView rootView = new RootView(30, 20, session.getImage());
        try {
          parser.updateRootComponent(myLastRenderedConfiguration, session, rootView);
        }
        catch (Throwable e) {
          myRootComponent = parser.getRootComponent();
          throw e;
        }
        RadViewComponent newRootComponent = parser.getRootComponent();

        newRootComponent.setClientProperty(ModelParser.XML_FILE_KEY, myXmlFile);
        newRootComponent.setClientProperty(ModelParser.MODULE_KEY, AndroidDesignerEditorPanel.this);
        newRootComponent.setClientProperty(TreeComponentDecorator.KEY, myTreeDecorator);

        PropertyParser propertyParser =
          new PropertyParser(getModule(), myProfileAction.getProfileManager().getSelectedTarget());
        newRootComponent.setClientProperty(PropertyParser.KEY, propertyParser);
        propertyParser.loadRecursive(newRootComponent);

        JPanel rootPanel = new JPanel(null);
        rootPanel.setBackground(Color.WHITE);
        rootPanel.add(rootView);

        removeNativeRoot();
        myRootComponent = newRootComponent;
        loadInspections(new EmptyProgressIndicator());
        updateInspections();
        myLayeredPane.add(rootPanel, LAYER_COMPONENT);

        myParseTime = false;

        runnable.run();
      }
    });
  }

  private void createRenderer(final String layoutXmlText,
                              final MyThrowable throwable,
                              final ThrowableConsumer<RenderSession, Throwable> runnable) {
    disposeRenderer();

    ApplicationManager.getApplication().saveAll();

    mySessionAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (mySession == null) {
          showProgress("Creating RenderLib...");
        }
      }
    }, 500);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          long time = System.currentTimeMillis();

          myProfileLastVersion = myProfileAction.getVersion();

          AndroidPlatform platform = AndroidPlatform.getInstance(getModule());
          if (platform == null) {
            throw new AndroidSdkNotConfiguredException();
          }

          AndroidFacet facet = AndroidFacet.getInstance(getModule());
          ProfileManager manager = myProfileAction.getProfileManager();

          LayoutDeviceConfiguration deviceConfiguration = manager.getSelectedDeviceConfiguration();
          if (deviceConfiguration == null) {
            throw new DeviceIsNotSpecifiedException();
          }

          myLastRenderedConfiguration = new FolderConfiguration();
          myLastRenderedConfiguration.set(deviceConfiguration.getConfiguration());
          myLastRenderedConfiguration.setUiModeQualifier(new UiModeQualifier(manager.getSelectedDockMode()));
          myLastRenderedConfiguration.setNightModeQualifier(new NightModeQualifier(manager.getSelectedNightMode()));

          LocaleData locale = manager.getSelectedLocale();
          if (locale == null) {
            throw new RenderingException("Locale is not specified");
          }
          myLastRenderedConfiguration.setLanguageQualifier(new LanguageQualifier(locale.getLanguage()));
          myLastRenderedConfiguration.setRegionQualifier(new RegionQualifier(locale.getRegion()));

          float xdpi = deviceConfiguration.getDevice().getXDpi();
          float ydpi = deviceConfiguration.getDevice().getYDpi();

          final boolean updatePalette = !Comparing.equal(myProfileAction.getProfileManager().getSelectedTarget(), myLastTarget);
          myLastTarget = manager.getSelectedTarget();
          ThemeData theme = manager.getSelectedTheme();

          if (myLastTarget == null || theme == null) {
            throw new RenderingException();
          }

          RenderingResult result = RenderUtil.renderLayout(getModule(),
                                                           layoutXmlText,
                                                           myFile,
                                                           null,
                                                           myLastTarget,
                                                           facet,
                                                           myLastRenderedConfiguration,
                                                           xdpi,
                                                           ydpi,
                                                           theme,
                                                           10000,
                                                           true);

          if (ApplicationManagerEx.getApplicationEx().isInternal()) {
            System.out.println("Render time: " + (System.currentTimeMillis() - time)); // XXX
          }

          if (result == null) {
            throw new RenderingException();
          }

          final RenderSession session = mySession = result.getSession();
          mySessionAlarm.cancelAllRequests();

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              try {
                if (!isProjectClosed()) {
                  hideProgress();
                  runnable.consume(session);
                  if (updatePalette) {
                    updatePalette(myLastTarget);
                  }
                }
              }
              catch (Throwable e) {
                myPSIChangeListener.clear();
                showError("Parsing error", throwable.wrap(e));
                myParseTime = false;
              }
            }
          });
        }
        catch (final Throwable e) {
          myPSIChangeListener.clear();
          mySessionAlarm.cancelAllRequests();

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              myPSIChangeListener.clear();
              showError("Render error", throwable.wrap(e));
              myParseTime = false;
            }
          });
        }
      }
    });
  }

  private void disposeRenderer() {
    if (mySession != null) {
      mySession.dispose();
      mySession = null;
    }
  }

  private void updateRenderer(final boolean updateProperties) {
    myParseTime = true;
    final String layoutXmlText = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        if (ModelParser.checkTag(myXmlFile.getRootTag())) {
          return myXmlFile.getText();
        }
        return ModelParser.NO_ROOT_CONTENT;
      }
    });
    createRenderer(layoutXmlText, new MyThrowable(), new ThrowableConsumer<RenderSession, Throwable>() {
      @Override
      public void consume(RenderSession session) throws Throwable {
        RadViewComponent rootComponent = (RadViewComponent)myRootComponent;
        RootView rootView = (RootView)rootComponent.getNativeComponent();
        rootView.setImage(session.getImage());
        ModelParser.updateRootComponent(myLastRenderedConfiguration, rootComponent, session, rootView);

        myParseTime = false;

        myLayeredPane.revalidate();
        myHorizontalCaption.update();
        myVerticalCaption.update();

        DesignerToolWindowManager.getInstance(getProject()).refresh(updateProperties);
      }
    });
  }

  private void removeNativeRoot() {
    if (myRootComponent != null) {
      Component component = ((RadVisualComponent)myRootComponent).getNativeComponent();
      if (component != null) {
        myLayeredPane.remove(component.getParent());
      }
    }
  }

  @Override
  protected void configureError(@NotNull ErrorInfo info) {
    Throwable renderCreator = null;
    if (info.myThrowable instanceof MyThrowable) {
      renderCreator = info.myThrowable;
      info.myThrowable = ((MyThrowable)info.myThrowable).original;
    }

    if (info.myThrowable instanceof AndroidSdkNotConfiguredException) {
      info.myShowLog = false;
      info.myShowStack = false;

      if (AndroidMavenUtil.isMavenizedModule(getModule())) {
        info.myMessages.add(new FixableMessageInfo(true, AndroidBundle.message("android.maven.cannot.parse.android.sdk.error",
                                                                               getModule().getName()), "", "", null, null));
      }
      else {
        info.myMessages.add(new FixableMessageInfo(true, "Please ", "configure", " Android SDK", new Runnable() {
          @Override
          public void run() {
            AndroidSdkUtils.openModuleDependenciesConfigurable(getModule());
          }
        }, null));
      }
    }
    else if (info.myThrowable instanceof DeviceIsNotSpecifiedException) {
      info.myShowLog = false;
      info.myShowStack = false;

      info.myMessages.add(new FixableMessageInfo(true, "Device is not specified, click ", "here", " to configure", new Runnable() {
        @Override
        public void run() {
          myProfileAction.getProfileManager().showCustomDevicesDialog();
          // XXX
        }
      }, null));
    }
    else if (((info.myThrowable instanceof ClassNotFoundException || info.myThrowable instanceof NoClassDefFoundError) &&
              myParseTime &&
              !info.myThrowable.toString().contains("jetbrains") &&
              !info.myThrowable.toString().contains("intellij")) ||
             info.myThrowable instanceof ProcessCanceledException) {
      info.myShowLog = false;
    }
    else {
      List<FixableIssueMessage> warnMessages = null;
      boolean renderError = info.myThrowable instanceof RenderingException;

      if (renderError) {
        RenderingException exception = (RenderingException)info.myThrowable;
        warnMessages = exception.getWarnMessages();

        if (StringUtil.isEmpty(exception.getPresentableMessage())) {
          Throwable[] causes = exception.getCauses();
          if (causes.length == 0) {
            info.myThrowable = new Exception(AndroidBundle.message("android.layout.preview.default.error.message"), exception);
          }
          else if (causes.length == 1) {
            info.myThrowable = causes[0];
          }
          else {
            info.myThrowable = new RenderingException(AndroidBundle.message("android.layout.preview.default.error.message"), causes);
          }
        }
      }

      if (warnMessages == null) {
        info.myShowMessage = myParseTime || renderError;
        info.myShowLog = !renderError;
      }
      else {
        info.myShowLog = false;
        info.myShowStack = true;

        for (FixableIssueMessage message : warnMessages) {
          info.myMessages.add(
            new FixableMessageInfo(false, message.myBeforeLinkText, message.myLinkText, message.myAfterLinkText, message.myQuickFix,
                                   message.myAdditionalFixes));
        }
      }
    }

    StringBuilder builder = new StringBuilder();

    builder.append("ActiveTool: ").append(myToolProvider.getActiveTool());
    builder.append("\nSDK: ");

    try {
      AndroidPlatform platform = AndroidPlatform.getInstance(getModule());
      IAndroidTarget target = platform.getTarget();
      builder.append(target.getFullName()).append(" - ").append(target.getVersion());
    }
    catch (Throwable e) {
      builder.append("<unknown>");
    }

    if (renderCreator != null) {
      builder.append("\nCreateRendererStack:\n");
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      renderCreator.printStackTrace(new PrintStream(stream));
      builder.append(stream.toString());
    }

    if (info.myThrowable instanceof IndexOutOfBoundsException && myRootComponent != null && mySession != null) {
      builder.append("\n-------- RadTree --------\n");
      ModelParser.printTree(builder, myRootComponent, 0);
      builder.append("\n-------- ViewTree(").append(mySession.getRootViews().size()).append(") --------\n");
      for (ViewInfo viewInfo : mySession.getRootViews()) {
        ModelParser.printTree(builder, viewInfo, 0);
      }
    }

    info.myMessage = builder.toString();
  }

  @Override
  protected void showErrorPage(ErrorInfo info) {
    myPSIChangeListener.clear();
    mySessionAlarm.cancelAllRequests();
    removeNativeRoot();
    super.showErrorPage(info);
  }

  public ProfileAction getProfileAction() {
    return myProfileAction;
  }

  @Override
  public void activate() {
    myProfileAction.externalUpdate();
    myPSIChangeListener.activate();

    if (myPSIChangeListener.isUpdateRenderer()) {
      updateRenderer(true);
    }
  }

  @Override
  public void deactivate() {
    myPSIChangeListener.deactivate();
  }

  @Override
  public void dispose() {
    myPSIChangeListener.dispose();
    super.dispose();
    disposeRenderer();
  }

  @Override
  @Nullable
  protected Module findModule(Project project, VirtualFile file) {
    Module module = super.findModule(project, file);
    if (module == null) {
      module = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
        @Override
        public Module compute() {
          return ModuleUtilCore.findModuleForPsiElement(myXmlFile);
        }
      });
    }
    return module;
  }

  @Override
  public String getPlatformTarget() {
    return "android";
  }

  @Override
  public TreeComponentDecorator getTreeDecorator() {
    return myTreeDecorator;
  }

  @Override
  public WrapInProvider getWrapInProvider() {
    if (myWrapInProvider == null) {
      myWrapInProvider = new AndroidWrapInProvider(getProject());
    }
    return myWrapInProvider;
  }

  private static final ComponentDecorator NON_RESIZE_DECORATOR = new NonResizeSelectionDecorator(Color.RED, 2);

  @Override
  protected ComponentDecorator getRootSelectionDecorator() {
    return NON_RESIZE_DECORATOR;
  }

  @Override
  protected EditOperation processRootOperation(OperationContext context) {
    return null;
  }

  @Override
  public List<PaletteGroup> getPaletteGroups() {
    return ViewsMetaManager.getInstance(getProject()).getPaletteGroups();
  }

  @Override
  @NotNull
  protected ComponentCreationFactory createCreationFactory(final PaletteItem paletteItem) {
    return new ComponentCreationFactory() {
      @NotNull
      @Override
      public RadComponent create() throws Exception {
        RadViewComponent component = ModelParser.createComponent(null, ((DefaultPaletteItem)paletteItem).getMetaModel());
        if (component instanceof IConfigurableComponent) {
          ((IConfigurableComponent)component).configure(myRootComponent);
        }
        return component;
      }
    };
  }

  @Override
  public ComponentPasteFactory createPasteFactory(String xmlComponents) {
    return new AndroidPasteFactory(getModule(), myLastTarget, xmlComponents);
  }

  private void updatePalette(IAndroidTarget target) {
    try {
      ClassLoader classLoader = ProjectClassLoader.create(target, getModule());

      for (PaletteGroup group : getPaletteGroups()) {
        for (PaletteItem item : group.getItems()) {
          if (item.getVersion() != null) {
            DefaultPaletteItem paletteItem = (DefaultPaletteItem)item;
            try {
              String className = paletteItem.getMetaModel().getTarget();
              classLoader.loadClass(className);
              paletteItem.setEnabled(true);
            }
            catch (Throwable e) {
              paletteItem.setEnabled(false);
            }
          }
        }
      }

      PaletteItem item = getActivePaletteItem();
      if (item != null && !item.isEnabled()) {
        activatePaletteItem(null);
      }

      PaletteToolWindowManager.getInstance(getProject()).refresh();
    }
    catch (Throwable e) {
    }
  }

  @Override
  public String getEditorText() {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return myXmlFile.getText();
      }
    });
  }

  @Override
  protected boolean execute(ThrowableRunnable<Exception> operation, boolean updateProperties) {
    if (!ReadonlyStatusHandler.ensureFilesWritable(getProject(), myFile)) {
      return false;
    }
    try {
      myPSIChangeListener.stop();
      operation.run();
      updateRenderer(updateProperties);
      return true;
    }
    catch (Throwable e) {
      showError("Execute command", e);
      return false;
    }
    finally {
      myPSIChangeListener.start();
    }
  }

  @Override
  protected void executeWithReparse(ThrowableRunnable<Exception> operation) {
    if (!ReadonlyStatusHandler.ensureFilesWritable(getProject(), myFile)) {
      return;
    }
    try {
      myPSIChangeListener.stop();
      operation.run();
      myPSIChangeListener.start();
      reparseFile();
    }
    catch (Throwable e) {
      showError("Execute command", e);
      myPSIChangeListener.start();
    }
  }

  @Override
  protected void execute(List<EditOperation> operations) {
    if (!ReadonlyStatusHandler.ensureFilesWritable(getProject(), myFile)) {
      return;
    }
    try {
      myPSIChangeListener.stop();
      for (EditOperation operation : operations) {
        operation.execute();
      }
      updateRenderer(true);
    }
    catch (Throwable e) {
      showError("Execute command", e);
    }
    finally {
      myPSIChangeListener.start();
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Inspections
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void loadInspections(ProgressIndicator progress) {
    if (myRootComponent != null) {
      ErrorAnalyzer.load(getProject(), myXmlFile, myRootComponent, progress);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private static class MyThrowable extends Throwable {
    public Throwable original;

    public MyThrowable wrap(Throwable original) {
      this.original = original;
      return this;
    }
  }
}