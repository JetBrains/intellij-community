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
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.resources.configuration.*;
import com.android.resources.NightMode;
import com.android.resources.UiMode;
import com.android.sdklib.IAndroidTarget;
import com.intellij.android.designer.componentTree.AndroidTreeDecorator;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.uipreview.*;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.swing.*;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class AndroidDesignerEditorPanel extends DesignerEditorPanel {
  private final TreeComponentDecorator myTreeDecorator = new AndroidTreeDecorator();
  private RenderSession mySession;

  public AndroidDesignerEditorPanel(@NotNull Module module, @NotNull VirtualFile file) {
    super(module, file);

    // TODO: (profile|device|target|...|theme) panel

    // TODO: use platform DOM

    // (temp code)

    try {
      AndroidPlatform platform = AndroidPlatform.getInstance(module);
      IAndroidTarget target = platform.getTarget();
      AndroidFacet facet = AndroidFacet.getInstance(module);

      LayoutDeviceManager layoutDeviceManager = new LayoutDeviceManager();
      layoutDeviceManager.loadDevices(platform.getSdk());
      LayoutDevice layoutDevice = layoutDeviceManager.getCombinedList().get(0);

      LayoutDeviceConfiguration deviceConfiguration = layoutDevice.getConfigurations().get(0);

      FolderConfiguration config = new FolderConfiguration();
      config.set(deviceConfiguration.getConfiguration());
      config.setUiModeQualifier(new UiModeQualifier(UiMode.NORMAL));
      config.setNightModeQualifier(new NightModeQualifier(NightMode.NIGHT));
      config.setLanguageQualifier(new LanguageQualifier());
      config.setRegionQualifier(new RegionQualifier());

      float xdpi = deviceConfiguration.getDevice().getXDpi();
      float ydpi = deviceConfiguration.getDevice().getYDpi();

      ThemeData theme = new ThemeData("Theme", false);

      String layoutXmlText = new String(file.contentsToByteArray());

      mySession = RenderUtil.createRenderSession(module.getProject(), layoutXmlText, file, target, facet, config, xdpi, ydpi, theme);

      InputStream stream = file.getInputStream();
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      parser.parse(stream, new DefaultHandler() {
        RadViewComponent myComponent;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
          myComponent = new RadViewComponent(myComponent, qName);
          if (myRootComponent == null) {
            myRootComponent = myComponent;
          }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
          if (myComponent != null) {
            myComponent = (RadViewComponent)myComponent.getParent();
          }
        }
      });
      stream.close();

      Result result = mySession.getResult();
      if (!result.isSuccess()) {
        Throwable exception = result.getException();
        if (exception != null) {
          throw exception;
        }
        else {
          throw new Exception("No session result");
        }
      }

      RootView rootView = new RootView(mySession.getImage(), 30, 20);
      updateRootComponent(mySession.getRootViews(), rootView);

      JPanel rootPanel = new JPanel(null);
      rootPanel.setBackground(Color.WHITE);
      rootPanel.add(rootView);

      myLayeredPane.add(rootPanel, LAYER_COMPONENT);

      showDesignerCard();
    }
    catch (Throwable e) {
      showError("Parse error: ", e);
    }
  }

  private void updateRootComponent(List<ViewInfo> views, JComponent nativeComponent) {
    RadViewComponent rootComponent = (RadViewComponent)myRootComponent;

    int size = views.size();
    if (size == 1) {
      RadViewComponent newRootComponent = new RadViewComponent(null, "Device Screen");
      newRootComponent.getChildren().add(rootComponent);
      rootComponent.setParent(newRootComponent);

      updateComponent(rootComponent, views.get(0), nativeComponent, 0, 0);

      myRootComponent = rootComponent = newRootComponent;
    }
    else {
      List<RadComponent> children = rootComponent.getChildren();
      for (int i = 0; i < size; i++) {
        updateComponent((RadViewComponent)children.get(i), views.get(i), nativeComponent, 0, 0);
      }
    }

    rootComponent.setNativeComponent(nativeComponent);
    rootComponent.setBounds(0, 0, nativeComponent.getWidth(), nativeComponent.getHeight());
  }

  private static void updateComponent(RadViewComponent component, ViewInfo view, JComponent nativeComponent, int parentX, int parentY) {
    component.setNativeComponent(nativeComponent);

    int left = parentX + view.getLeft();
    int top = parentY + view.getTop();
    component.setBounds(left, top, view.getRight() - view.getLeft(), view.getBottom() - view.getTop());

    List<ViewInfo> views = view.getChildren();
    List<RadComponent> children = component.getChildren();
    int size = views.size();

    for (int i = 0; i < size; i++) {
      updateComponent((RadViewComponent)children.get(i), views.get(i), nativeComponent, left, top);
    }
  }

  @Override
  public TreeComponentDecorator getTreeDecorator() {
    return myTreeDecorator;
  }

  private static class RootView extends JComponent {
    private final BufferedImage myImage;

    public RootView(BufferedImage image, int x, int y) {
      myImage = image;
      setBounds(x, y, image.getWidth(), image.getHeight());
    }

    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);
      g.drawImage(myImage, 0, 0, null);
    }
  }
}