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
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.resources.configuration.*;
import com.intellij.android.designer.actions.ProfileAction;
import com.intellij.android.designer.componentTree.AndroidTreeDecorator;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.PropertyParser;
import com.intellij.android.designer.model.viewAnimator.RadViewAnimatorLayout;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.profile.ProfileManager;
import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.selection.NonResizeSelectionDecorator;
import com.intellij.designer.designSurface.tools.ComponentCreationFactory;
import com.intellij.designer.designSurface.tools.ComponentPasteFactory;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.palette.Item;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.uipreview.LayoutDeviceConfiguration;
import org.jetbrains.android.uipreview.LocaleData;
import org.jetbrains.android.uipreview.RenderUtil;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.android.util.AndroidSdkNotConfiguredException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class AndroidDesignerEditorPanel extends DesignerEditorPanel {
  private final TreeComponentDecorator myTreeDecorator = new AndroidTreeDecorator();
  private final XmlFile myXmlFile;
  private final ExternalPSIChangeListener myPSIChangeListener;
  private final ProfileAction myProfileAction;
  private volatile RenderSession mySession;
  private boolean myParseTime;

  public AndroidDesignerEditorPanel(@NotNull Module module, @NotNull VirtualFile file) {
    super(module, file);

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

    showProgress("Load configuration");
    myProfileAction = new ProfileAction(this, new Runnable() {
      @Override
      public void run() {
        myActionPanel.update();
        if (myRootComponent == null) {
          myPSIChangeListener.start();
          myPSIChangeListener.addRequest();
        }
        else {
          myPSIChangeListener.addRequest(new Runnable() {
            @Override
            public void run() {
              updateRenderer(true);
            }
          });
        }
      }
    });
  }

  @Override
  public void updateTreeArea(EditableArea area) {
    area.addSelectionListener(new ComponentSelectionListener() {
      @Override
      public void selectionChanged(EditableArea area) {
        List<RadComponent> selection = area.getSelection();
        if (selection.size() == 1) {
          final RadComponent component = selection.get(0);
          final RadComponent parent = component.getParent();
          if (parent instanceof RadViewComponent && parent.getLayout() instanceof RadViewAnimatorLayout) {
            ApplicationManager.getApplication().invokeLater(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    int index = parent.getChildren().indexOf(component);
                    Object parentView =
                      ((RadViewComponent)parent).getViewInfo().getViewObject();
                    Method method =
                      parentView.getClass().getMethod("setDisplayedChild", int.class);
                    method.invoke(parentView, index);

                    Result result = mySession.render();

                    if (!result.isSuccess()) {
                      System.out.println(
                        "No re render session: " +
                        result.getErrorMessage() +
                        " : " +
                        result.getStatus() +
                        " : " +
                        result.getData() +
                        " : " +
                        result.getException());
                    }
                    else {
                      RadViewComponent rootComponent = (RadViewComponent)myRootComponent;
                      RootView rootView = (RootView)rootComponent.getNativeComponent();
                      rootView.setImage(mySession.getImage());
                      ModelParser.updateRootComponent(rootComponent, mySession, rootView);

                      myLayeredPane.repaint();

                      DesignerToolWindowManager.getInstance(getProject()).refresh(true);
                    }
                  }
                  catch (Throwable e) {
                    showError("reRender error: ", e);
                  }
                }
              }, new Condition() {
                @Override
                public boolean value(Object o) {
                  return mySession == null;
                }
              }
            );
          }
        }
      }
    });
  }

  private void reparseFile() {
    myToolProvider.loadDefaultTool();
    mySurfaceArea.deselectAll();

    parseFile(new Runnable() {
      @Override
      public void run() {
        showDesignerCard();
        myLayeredPane.repaint();

        DesignerToolWindowManager.getInstance(getProject()).refresh(true);
      }
    });
  }

  private void parseFile(final Runnable runnable) {
    myParseTime = true;
    final ModelParser parser = new ModelParser(getProject(), myXmlFile);
    createRenderer(parser.getLayoutXmlText(), new ThrowableRunnable<Throwable>() {
      @Override
      public void run() throws Throwable {
        checkRenderer();

        RootView rootView = new RootView(mySession.getImage(), 30, 20);
        parser.updateRootComponent(mySession, rootView);
        RadViewComponent newRootComponent = parser.getRootComponent();

        newRootComponent.setClientProperty(ModelParser.XML_FILE_KEY, myXmlFile);

        PropertyParser propertyParser = new PropertyParser(myModule, myProfileAction.getProfileManager().getSelectedTarget());
        newRootComponent.setClientProperty(PropertyParser.KEY, propertyParser);
        propertyParser.loadRecursive(newRootComponent);

        JPanel rootPanel = new JPanel(null);
        rootPanel.setBackground(Color.WHITE);
        rootPanel.add(rootView);

        removeNativeRoot();
        myRootComponent = newRootComponent;
        myLayeredPane.add(rootPanel, LAYER_COMPONENT);

        myParseTime = false;

        runnable.run();
      }
    });
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
    createRenderer(layoutXmlText, new ThrowableRunnable<Throwable>() {
      @Override
      public void run() throws Throwable {
        checkRenderer();

        RadViewComponent rootComponent = (RadViewComponent)myRootComponent;
        RootView rootView = (RootView)rootComponent.getNativeComponent();
        rootView.setImage(mySession.getImage());
        ModelParser.updateRootComponent(rootComponent, mySession, rootView);

        myParseTime = false;

        myLayeredPane.repaint();

        DesignerToolWindowManager.getInstance(getProject()).refresh(updateProperties);
      }
    });
  }

  private void checkRenderer() throws Throwable {
    Result result = mySession.getResult();
    if (!result.isSuccess()) {
      System.out.println(
        "No session: " + result.getErrorMessage() + " : " + result.getStatus() + " : " + result.getData() + " : " + result.getException());
      Throwable exception = result.getException();
      if (exception != null) {
        throw exception;
      }
      else {
        throw new RenderingException("No session result");
      }
    }
  }

  private void removeNativeRoot() {
    if (myRootComponent != null) {
      myLayeredPane.remove(((RadViewComponent)myRootComponent).getNativeComponent().getParent());
    }
  }

  private void createRenderer(final String layoutXmlText, final ThrowableRunnable<Throwable> runnable) {
    if (mySession == null) {
      ApplicationManager.getApplication().invokeLater(
        new Runnable() {
          @Override
          public void run() {
            if (mySession == null) {
              showProgress("Create RenderLib");
            }
          }
        }, new Condition() {
          @Override
          public boolean value(Object o) {
            return mySession != null;
          }
        }
      );
    }
    else {
      disposeSession();
    }

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          AndroidPlatform platform = AndroidPlatform.getInstance(myModule);
          if (platform == null) {
            throw new AndroidSdkNotConfiguredException();
          }

          AndroidFacet facet = AndroidFacet.getInstance(myModule);
          ProfileManager manager = myProfileAction.getProfileManager();

          LayoutDeviceConfiguration deviceConfiguration = manager.getSelectedDeviceConfiguration();
          if (deviceConfiguration == null) {
            throw new RenderingException("Device is not specified");
          }

          FolderConfiguration config = new FolderConfiguration();
          config.set(deviceConfiguration.getConfiguration());
          config.setUiModeQualifier(new UiModeQualifier(manager.getSelectedDockMode()));
          config.setNightModeQualifier(new NightModeQualifier(manager.getSelectedNightMode()));

          LocaleData locale = manager.getSelectedLocale();
          if (locale == null) {
            throw new RenderingException("Locale is not specified");
          }
          config.setLanguageQualifier(new LanguageQualifier(locale.getLanguage()));
          config.setRegionQualifier(new RegionQualifier(locale.getRegion()));

          float xdpi = deviceConfiguration.getDevice().getXDpi();
          float ydpi = deviceConfiguration.getDevice().getYDpi();

          mySession = RenderUtil
            .createRenderSession(getProject(), layoutXmlText, myFile, manager.getSelectedTarget(), facet, config, xdpi, ydpi,
                                 manager.getSelectedTheme());

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              try {
                hideProgress();
                runnable.run();
              }
              catch (Throwable e) {
                showError("Parse error: ", e);
                myParseTime = false;
              }
            }
          });
        }
        catch (final Throwable e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              showError("Render session error: ", e);
              myParseTime = false;
            }
          });
        }
      }
    });
  }

  private void disposeSession() {
    if (mySession != null) {
      mySession.dispose();
      mySession = null;
    }
  }

  @Override
  protected void configureError(ErrorInfo info) {
    if (info.throwable instanceof AndroidSdkNotConfiguredException) {
      info.message = "Please configure Android SDK";
      info.stack = false;
    }
    else if (!(info.throwable instanceof RenderingException)) {
      info.show = myParseTime;
      info.log = true;
    }
  }

  @Override
  protected void showErrorPage(ErrorInfo info) {
    removeNativeRoot();
    super.showErrorPage(info);
  }

  public ProfileAction getProfileAction() {
    return myProfileAction;
  }

  @Override
  public void activate() {
    myProfileAction.externalUpdate();
  }

  @Override
  public void dispose() {
    myPSIChangeListener.stop();
    super.dispose();
    disposeSession();
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
  protected ComponentDecorator getRootSelectionDecorator() {
    return new NonResizeSelectionDecorator(Color.RED, 1);
  }

  @Override
  protected EditOperation processRootOperation(OperationContext context) {
    return null;
  }

  @Override
  @NotNull
  protected ComponentCreationFactory createCreationFactory(final Item paletteItem) {
    return new ComponentCreationFactory() {
      @NotNull
      @Override
      public RadComponent create() throws Exception {
        return ModelParser.createComponent(null, paletteItem.getMetaModel());
      }
    };
  }

  @Override
  public ComponentPasteFactory createPasteFactory(String xmlComponents) {
    return null; // TODO: Auto-generated method stub
  }

  @Override
  protected boolean execute(ThrowableRunnable<Exception> operation, boolean updateProperties) {
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
  protected void execute(List<EditOperation> operations) {
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
}