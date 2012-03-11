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
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.profile.ProfileManager;
import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.designSurface.ComponentDecorator;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.selection.NonResizeSelectionDecorator;
import com.intellij.designer.designSurface.tools.ComponentCreationFactory;
import com.intellij.designer.designSurface.tools.ComponentPasteFactory;
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
import org.jetbrains.android.uipreview.*;
import org.jetbrains.android.util.AndroidSdkNotConfiguredException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
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
              updateRenderer();
            }
          });
        }
      }
    });
  }

  private void reparseFile() {
    try {
      myToolProvider.loadDefaultTool();
      mySurfaceArea.deselectAll();

      parseFile(new Runnable() {
        @Override
        public void run() {
          showDesignerCard();
          myLayeredPane.repaint();

          DesignerToolWindowManager.getInstance(getProject()).refresh();
        }
      });
    }
    catch (Throwable e) {
      showError("Parse error: ", e);
    }
  }

  private void parseFile(final Runnable runnable) throws Exception {
    final ModelParser parser = new ModelParser(getProject(), myXmlFile);
    createRenderer(parser.getLayoutXmlText(), new ThrowableRunnable<Throwable>() {
      @Override
      public void run() throws Throwable {
        checkRenderer(false);

        RootView rootView = new RootView(mySession.getImage(), 30, 20);
        parser.updateRootComponent(mySession, rootView);

        new PropertyParser(myModule, myProfileAction.getProfileManager().getSelectedTarget()).loadRecursive(parser.getRootComponent());

        JPanel rootPanel = new JPanel(null);
        rootPanel.setBackground(Color.WHITE);
        rootPanel.add(rootView);

        removeNativeRoot();
        myRootComponent = parser.getRootComponent();
        myLayeredPane.add(rootPanel, LAYER_COMPONENT);

        runnable.run();
      }
    });
  }

  private void updateRenderer() {
    final String layoutXmlText = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return myXmlFile.getText();
      }
    });
    createRenderer(layoutXmlText, new ThrowableRunnable<Throwable>() {
      @Override
      public void run() throws Throwable {
        updateRenderer(false);
      }
    });
  }

  private void updateRenderer(boolean render) throws Throwable {
    checkRenderer(render);

    RadViewComponent rootComponent = (RadViewComponent)myRootComponent;
    RootView rootView = (RootView)rootComponent.getNativeComponent();
    rootView.setImage(mySession.getImage());
    ModelParser.updateRootComponent(rootComponent, mySession, rootView);

    myLayeredPane.repaint();
  }

  private void checkRenderer(boolean render) throws Throwable {
    Result result = render ? mySession.render() : mySession.getResult();
    if (!result.isSuccess()) {
      System.out.println(
        "No session: " + result.getErrorMessage() + " : " + result.getStatus() + " : " + result.getData() + " : " + result.getException());
      Throwable exception = result.getException();
      if (exception != null) {
        throw exception;
      }
      else {
        throw new Exception("No session result");
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
          System.out.println(mySession + " | " + mySession.getClass());

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              try {
                hideProgress();
                runnable.run();
              }
              catch (Throwable e) {
                showError("Parse error: ", e);
              }
            }
          });
        }
        catch (RenderingException e) {
          // TODO
          e.printStackTrace();
        }
        catch (AndroidSdkNotConfiguredException e) {
          // TODO
          e.printStackTrace();
        }
        catch (final Throwable e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              showError("Render session error: ", e);
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
  public void showError(@NonNls String message, Throwable e) {
    removeNativeRoot();
    super.showError(message, e);
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
  protected ComponentCreationFactory createCreationFactory(Item paletteItem) {
    return null;  // TODO: Auto-generated method stub
  }

  @Override
  public ComponentPasteFactory createPasteFactory(String xmlComponents) {
    return null; // TODO: Auto-generated method stub
  }

  @Override
  protected boolean execute(ThrowableRunnable<Exception> operation) {
    try {
      myPSIChangeListener.stop();
      operation.run();
      updateRenderer(true);
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

  private static class RootView extends JComponent {
    private int myX;
    private int myY;
    private BufferedImage myImage;

    public RootView(BufferedImage image, int x, int y) {
      myX = x;
      myY = y;
      setImage(image);
    }

    public void setImage(BufferedImage image) {
      myImage = image;
      setBounds(myX, myY, image.getWidth(), image.getHeight());
    }

    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);
      g.drawImage(myImage, 0, 0, null);
    }
  }
}