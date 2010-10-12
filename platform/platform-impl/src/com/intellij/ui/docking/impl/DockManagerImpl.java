/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.docking.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.docking.DragSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

public class DockManagerImpl extends DockManager {

  private Project myProject;

  private Set<DockContainer> myContainers = new HashSet<DockContainer>();
  private MyDragSession myCurrentDragSession;

  public DockManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void register(final DockContainer container, @Nullable Disposable parent) {
    myContainers.add(container);
    Disposer.register(parent != null ? parent : myProject, new Disposable() {
      @Override
      public void dispose() {
        myContainers.remove(container);
      }
    });
  }

  @Override
  public DragSession createDragSession(MouseEvent mouseEvent, DockableContent content) {
    stopCurrentDragSession();
    myCurrentDragSession = new MyDragSession(mouseEvent, content);
    return myCurrentDragSession;
  }


  private void stopCurrentDragSession() {
    if (myCurrentDragSession != null) {
      myCurrentDragSession.cancel();
      myCurrentDragSession = null;
    }
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "DockManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  private class MyDragSession implements DragSession {

    private JWindow myWindow;
    private Image myThumbnail;
    private DockableContent myContent;

    private MyDragSession(MouseEvent me, DockableContent content) {
      myWindow = new JWindow();
      myContent = content;

      Image previewImage = content.getPreviewImage();

      double requiredSize = 220;

      double width = previewImage.getWidth(null);
      double height = previewImage.getHeight(null);

      double ratio;
      if (width > height) {
        ratio = requiredSize / width;
      } else {
        ratio = requiredSize / height;
      }

      BufferedImage buffer = new BufferedImage((int)width, (int)height, BufferedImage.TYPE_INT_ARGB);
      buffer.createGraphics().drawImage(previewImage, 0, 0, (int)width, (int)height, null);

      myThumbnail = buffer.getScaledInstance((int)(width * ratio), (int)(height * ratio), Image.SCALE_SMOOTH);

      myWindow.getContentPane().setLayout(new BorderLayout());
      JLabel label = new JLabel(new ImageIcon(myThumbnail));
      label.setBorder(new LineBorder(Color.lightGray));
      myWindow.getContentPane().add(label, BorderLayout.CENTER);

      setLocationFrom(me);

      myWindow.setVisible(true);

      WindowManagerEx.getInstanceEx().setAlphaModeEnabled(myWindow, true);
      WindowManagerEx.getInstanceEx().setAlphaModeRatio(myWindow, 0.1f);
      myWindow.getRootPane().putClientProperty("Window.shadow", Boolean.FALSE);
    }

    private void setLocationFrom(MouseEvent me) {
      Point showPoint = me.getPoint();
      SwingUtilities.convertPointToScreen(showPoint, me.getComponent());

      showPoint.x -= myThumbnail.getWidth(null) / 2;
      showPoint.y += 10;
      myWindow.setBounds(new Rectangle(showPoint, new Dimension(myThumbnail.getWidth(null), myThumbnail.getHeight(null))));
    }

    @Override
    public void process(MouseEvent e) {
      if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
        setLocationFrom(e);
      } else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
        createNewDockContainerFor(myContent, e);
        stopCurrentDragSession();
      }
    }

    public void cancel() {
      myWindow.dispose();
    }
  }

  private void createNewDockContainerFor(DockableContent content, MouseEvent event) {
    DockContainer container = content.getContainerFactory().createContainer();
    register(container, null);
    DockWindow window = new DockWindow(myProject, container);
    window.show();

    Dimension size = content.getPreferredSize();
    Point showPoint = event.getPoint();
    SwingUtilities.convertPointToScreen(showPoint, event.getComponent());
    showPoint.x -= size.width / 2;
    showPoint.y += size.width / 2;

    window.setLocation(showPoint);

    window.show();

    window.setSize(size);
    container.add(content, new RelativePoint(event));
  }

  private class DockWindow extends FrameWrapper {

    private DockContainer myContainer;

    private DockWindow(Project project, DockContainer container) {
      myContainer = container;
      setProject(project);
      setComponent(myContainer.getComponent());
    }
  }
}
