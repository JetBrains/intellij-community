/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class FogLayer extends JComponent implements AWTEventListener, Runnable, Disposable {
  private static final int MAX_SPOT_NUMBER = 20000;
  private static final int LAYER_NUMBER = 5;
  private static final int INIT_DELAY = 5000;
  private static final int IDLE_THRESHOLD = 20000;
  private static final int FADE_CYCLE = 20000;
  private static final int EYE_SIZE = 50;
  private static final int REPAINT_INTERVAL = 66;//ms
  private static final Color BASE = Color.DARK_GRAY;
  private static final Color D_BASE = Color.LIGHT_GRAY;
  private static final Color FOG = new JBColor(ColorUtil.toAlpha(BASE, 1), ColorUtil.toAlpha(D_BASE, 1));
  private static final double MIN_ROTATION_RATE = .0016;
  private static final double MAX_ROTATION_RATE = .002;
  private static final double MIN_SUBROTATION_RATE = 0.01;
  private static final double MAX_SUBROTATION_RATE = 0.02;
  private static final int SPOT_RADIUS_FACTOR = 150;


  private final Random myRandom = new Random();
  private final Alarm myAlarm;
  private final AtomicBoolean myInitialized = new AtomicBoolean(false);
  private final AtomicBoolean myDisposed = new AtomicBoolean(false);

  private BufferedImage myTexture;
  private HashMap<Integer, BufferedImage> myCache = new HashMap<Integer, BufferedImage>();


  private double[] myTextureAngle = new double[LAYER_NUMBER];
  private double[] myTextureRate = new double[LAYER_NUMBER];//rotation rate

  private double[] myCenterAngle = new double[LAYER_NUMBER];
  private double[] myCenterRate = new double[LAYER_NUMBER];
  private long myLastTime = System.currentTimeMillis();
  private Point myPoint = null;
  private long myLastPaintTime = -1;
  private long myStartPaintTime = -1;

  private int myEffectiveRadius;
  private final ScheduledFuture<?> myFuture;

  static boolean isAvailable() {
    return UIUtil.isFD()         &&
           !hasBgTasks()
           && Runtime.getRuntime().availableProcessors() >= 4;
  }

  private static boolean hasBgTasks() {
    for (Frame frame : Frame.getFrames()) {
      if (frame instanceof IdeFrameImpl) {
        final StatusBar bar = ((IdeFrameImpl)frame).getStatusBar();
        if (bar instanceof IdeStatusBarImpl) {
          final List<Pair<TaskInfo, ProgressIndicator>> processes = ((IdeStatusBarImpl)bar).getBackgroundProcesses();
          if (!processes.isEmpty()) return true;
        }
      }
    }
    return false;
  }


  FogLayer(Disposable parent) {
    setOpaque(false);
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    myFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        if (System.currentTimeMillis() - myLastTime >= IDLE_THRESHOLD &&
            myPoint != null &&
            myAlarm.isEmpty() &&
            myInitialized.get() &&
            !myDisposed.get()) {
          repaint();
        }
      }
    }, (long)INIT_DELAY, (long)REPAINT_INTERVAL, TimeUnit.MILLISECONDS);
    Disposer.register(parent, this);
  }

  @Override
  public void dispose() {
    if (myDisposed.get()) return;
    myFuture.cancel(true);
    myDisposed.set(true);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    Toolkit.getDefaultToolkit().addAWTEventListener(this,
                                                    AWTEvent.KEY_EVENT_MASK |
                                                    AWTEvent.MOUSE_EVENT_MASK |
                                                    AWTEvent.MOUSE_MOTION_EVENT_MASK
    );
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    Toolkit.getDefaultToolkit().removeAWTEventListener(this);
  }

  //update textures
  @Override
  public void run() {
    if (myPoint == null || myDisposed.get()) return;
    if (Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory() + Runtime.getRuntime().freeMemory() < 1L << 25) return;
    int width = getWidth();
    int height = getHeight();
    myEffectiveRadius = (int)(Math.max(
      Math.max(myPoint.distance(0, 0), myPoint.distance(width, 0)),
      Math.max(myPoint.distance(0, height), myPoint.distance(width, height)))) + 2 * EYE_SIZE;

    for (int i = 0; i < LAYER_NUMBER; i++) {
      myTextureAngle[i] = getRandomDouble(0, 2 * Math.PI);
      myTextureRate[i] = getRandomDouble(MIN_ROTATION_RATE, MAX_ROTATION_RATE) * ((myEffectiveRadius < 1000) ? 2 : 1);
      myCenterAngle[i] = getRandomDouble(0, 2 * Math.PI);
      myCenterRate[i] = -getRandomDouble(MIN_SUBROTATION_RATE, MAX_SUBROTATION_RATE) * (myRandom.nextDouble() > .5 ? 1 : -1);
    }
    myTexture = GraphicsEnvironment.getLocalGraphicsEnvironment()
      .getDefaultScreenDevice().getDefaultConfiguration()
      .createCompatibleImage(2 * myEffectiveRadius, 2 * myEffectiveRadius, Transparency.TRANSLUCENT);
    myLastPaintTime = -1;
    Graphics2D graphics = (Graphics2D)myTexture.getGraphics();
    for (int j = 0; j < MAX_SPOT_NUMBER; j++) {
      double v = (1 + Math.cos(myRandom.nextDouble() * Math.PI)) / 2;
      v = Math.pow(v, .18);
      double spotDistance = 2 * EYE_SIZE + (myEffectiveRadius - 2 * EYE_SIZE) * v;
      double distanceRatio = spotDistance / myEffectiveRadius;
      double minR = 30;
      double maxR = Math.max(Math.sqrt(distanceRatio) * SPOT_RADIUS_FACTOR, minR);
      int spotSize = (int)getRandomDouble(minR, maxR);
      spotSize -= spotSize % 4;
      double spotPhi = getRandomDouble(0, 2 * Math.PI);
      BufferedImage ellipseImage = myCache.get(spotSize);
      if (ellipseImage == null) {
        myCache.put(spotSize, ellipseImage = GraphicsEnvironment.getLocalGraphicsEnvironment()
          .getDefaultScreenDevice().getDefaultConfiguration()
          .createCompatibleImage(spotSize, spotSize, Transparency.TRANSLUCENT));
        Graphics2D g2d = (Graphics2D)ellipseImage.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setColor(FOG);
        g2d.fill(new Ellipse2D.Double(0, 0, spotSize, spotSize)
        );
      }
      graphics.drawImage(ellipseImage, (int)(myEffectiveRadius + spotDistance * Math.cos(spotPhi) - spotSize / 2),
                         (int)(myEffectiveRadius - spotDistance * Math.sin(spotPhi) - spotSize / 2), this);
    }
    myCache.clear();
    myStartPaintTime = -1;
    myInitialized.set(true);
  }

  private double getRandomDouble(double min, double max) {
    return min + myRandom.nextDouble() * (max - min);
  }

  @Override
  public void reshape(int x, int y, int w, int h) {
    int width = getWidth();
    int height = getHeight();
    super.reshape(x, y, w, h);
    if (width != w || height != h) {
      scheduleUpdate();
    }
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    myLastTime = System.currentTimeMillis();
    myInitialized.set(false);
    if (event instanceof KeyEvent) {
      Component component = ((KeyEvent)event).getComponent();
      if (component instanceof EditorComponentImpl) {
        EditorImpl editor = ((EditorComponentImpl)component).getEditor();
        Point position = editor.visualPositionToXY(editor.getCaretModel().getVisualPosition());
        myPoint = SwingUtilities.convertPoint(component, position, this);
      }
      else {
        Point position = new Point(component.getWidth() / 2, component.getHeight() / 2);
        myPoint = SwingUtilities.convertPoint(component, position, this);
      }
    }
    if (event instanceof MouseEvent) {
      if (event.getID() == MouseEvent.MOUSE_EXITED) {
        myPoint = null;
      }
      else {
        Point point = ((MouseEvent)event).getPoint();
        myPoint = SwingUtilities.convertPoint(((MouseEvent)event).getComponent(), point, this);
      }
    }
    if (myPoint != null) {
      myPoint.x = Math.min(getWidth(), Math.max(0, myPoint.x));
      myPoint.y = Math.min(getHeight(), Math.max(0, myPoint.y));
      scheduleUpdate();
    }
  }

  private void scheduleUpdate() {
    if (myDisposed.get()) return;
    myInitialized.set(false);
    repaint();
    myTexture = null;
    myCache.clear();
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(this, INIT_DELAY);
  }

  @Override
  protected void paintComponent(Graphics g) {
    long now = System.currentTimeMillis();
    if (now - myLastTime < IDLE_THRESHOLD || myPoint == null || !myAlarm.isEmpty() || !myInitialized.get()) {
      return;
    }
    Window window = SwingUtilities.getWindowAncestor(this);
    if (window == null || !window.isActive()) {
      myPoint = null;
      return;
    }
    if (myLastPaintTime == -1) myLastPaintTime = now - REPAINT_INTERVAL;
    if (myStartPaintTime == -1) myStartPaintTime = now;

    long passedTime = now - myLastPaintTime;

    for (int i = 0; i < LAYER_NUMBER; i++) {
      myTextureAngle[i] += (myTextureRate[i] * passedTime) / REPAINT_INTERVAL;
      myCenterAngle[i] += (myCenterRate[i] * passedTime) / REPAINT_INTERVAL;
    }

    double linearProgress = (double)(myLastPaintTime - myStartPaintTime) / FADE_CYCLE;
    if (linearProgress > 1) {
      myPoint = null;
      repaint();
      return;
    }
    double progress = (1 - Math.cos(2 * Math.PI * linearProgress)) / 2;


    Graphics2D g2d = (Graphics2D)g.create();
    try {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
      AffineTransform pointTransform = AffineTransform.getTranslateInstance(myPoint.x, myPoint.y);
      AffineTransform oldTransform = g2d.getTransform();
      pointTransform.concatenate(oldTransform);
      float textureProgress = (float)(progress < 1 ? Math.sqrt(progress) * (LAYER_NUMBER + 1) : LAYER_NUMBER);
      int textureNumber = (int)textureProgress;
      float localProgress = textureProgress - textureNumber;
      for (int i = 0; i < textureNumber; i++) {
        AffineTransform t = AffineTransform.getRotateInstance(myTextureAngle[i]);
        t.concatenate(
          AffineTransform.getTranslateInstance(EYE_SIZE * Math.cos(myCenterAngle[i]), -EYE_SIZE * Math.sin(myCenterAngle[i])));
        t.preConcatenate(pointTransform);
        g2d.setTransform(t);
        if (i == textureNumber - 1 && progress < 1) {
          g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, localProgress));
        }
        g2d.drawImage(myTexture, -myEffectiveRadius, -myEffectiveRadius, this);
      }
    }
    finally {
      g2d.dispose();
    }
    myLastPaintTime = now;
  }

  @Override
  public String toString() {
    return "Day#16161";
  }
}
