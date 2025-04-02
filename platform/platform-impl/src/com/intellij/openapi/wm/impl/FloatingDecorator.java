// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.WindowInfo;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.toolWindow.InternalDecoratorImpl;
import com.intellij.ui.*;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.Alarm;
import com.intellij.util.MathUtil;
import com.intellij.util.SingleEdtTaskScheduler;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBInsets;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

@ApiStatus.Internal
public final class FloatingDecorator extends JDialog implements FloatingDecoratorMarker, ToolWindowExternalDecorator, DisposableWindow {
  private static final Logger LOG = Logger.getInstance(FloatingDecorator.class);

  static final int DIVIDER_WIDTH = 3;

  private static final int ANCHOR_TOP=1;
  private static final int ANCHOR_LEFT=2;
  private static final int ANCHOR_BOTTOM=4;
  private static final int ANCHOR_RIGHT=8;

  private static final int DELAY = 15; // Delay between frames
  private static final int TOTAL_FRAME_COUNT = 7; // Total number of frames in animation sequence

  private final MyUISettingsListener uiSettingsListener;
  private final @NotNull InternalDecoratorImpl myDecorator;
  private WindowInfo myInfo;
  private final @NotNull ToolWindowExternalDecoratorBoundsHelper myBoundsHelper = new ToolWindowExternalDecoratorBoundsHelper(this);

  private final Disposable disposable = Disposer.newDisposable();
  private boolean myDisposed = false;
  private final SingleEdtTaskScheduler delayAlarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler(); // determines a moment when a tool window should become transparent
  private final Alarm myFrameTicker; // Determines moments of rendering of next frame
  private final MyAnimator myAnimator; // Renders alpha ratio
  private int myCurrentFrame; // current frame in transparency animation
  private float myStartRatio;
  private float myEndRatio; // start and end alpha ratio for transparency animation

  FloatingDecorator(@NotNull JFrame owner, @NotNull InternalDecoratorImpl decorator) {
    super(owner, decorator.toolWindow.getStripeTitle());
    myDecorator = decorator;
    ClientProperty.put(this, DECORATOR_PROPERTY, this);

    MnemonicHelper.init(getContentPane());

    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    JComponent contentPane = (JComponent)getContentPane();
    contentPane.setLayout(new BorderLayout());

    if (SystemInfo.isWindows) {
      setUndecorated(true);
      contentPane.add(new BorderItem(ANCHOR_TOP), BorderLayout.NORTH);
      contentPane.add(new BorderItem(ANCHOR_LEFT), BorderLayout.WEST);
      contentPane.add(new BorderItem(ANCHOR_BOTTOM), BorderLayout.SOUTH);
      contentPane.add(new BorderItem(ANCHOR_RIGHT), BorderLayout.EAST);
      contentPane.add(decorator, BorderLayout.CENTER);
    }
    else {
      // Due to JDK's bug #4234645 we cannot support custom decoration on Linux platform.
      // The problem is that Window.setLocation() doesn't work properly wjen the dialod is displayable.
      // Therefore we use native WM decoration.
      contentPane.add(decorator, BorderLayout.CENTER);
    }

    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    ToolWindowImpl toolWindow = decorator.toolWindow;
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent event) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Updating floating window " + toolWindow.getId() + " bounds because it's closed: " + getBounds());
        }
        toolWindow.toolWindowManager.movedOrResized(decorator);
        toolWindow.toolWindowManager.hideToolWindow(toolWindow.getId(), false);
      }
    });
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentMoved(ComponentEvent e) {
        if (LOG.isTraceEnabled()) {
          LOG.trace(
            "Floating tool window " + toolWindow.getId() + " moved to " + getBounds() + ", scheduling bounds update"
          );
        }
        toolWindow.onMovedOrResized();
      }
      // resize is handled by the internal decorator
    });

    myFrameTicker = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable);
    myAnimator = new MyAnimator();
    myCurrentFrame = 0;
    myStartRatio = 0.0f;
    myEndRatio = 0.0f;

    uiSettingsListener = new MyUISettingsListener();

    IdeGlassPaneImpl ideGlassPane = new IdeGlassPaneImpl(getRootPane(), true);
    getRootPane().setGlassPane(ideGlassPane);

    //workaround: we need to add this IdeGlassPane instance as dispatcher in IdeEventQueue
    ideGlassPane.addMousePreprocessor(new MouseAdapter() {
    }, disposable);

    if (SystemInfo.isWindows && WindowRoundedCornersManager.isAvailable()) {
      WindowRoundedCornersManager.setRoundedCorners(this);
    }
  }

  @Override
  public void show(){
    ComponentUtil.decorateWindowHeader(rootPane);
    ToolbarService.Companion.getInstance().setTransparentTitleBar(this, rootPane, runnable -> {
      Disposer.register(disposable, () -> runnable.run());
      return Unit.INSTANCE;
    });
    boolean isActive = myInfo.isActiveOnStart();

    setAutoRequestFocus(isActive);
    try {
      super.show();
    } finally {
      setAutoRequestFocus(true);
    }

    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.getState().getEnableAlphaMode()) {
      WindowManagerEx windowManager = WindowManagerEx.getInstanceEx();
      windowManager.setAlphaModeEnabled(this, true);
      if (isActive) {
        windowManager.setAlphaModeRatio(this, 0.0f);
      }
      else {
        windowManager.setAlphaModeRatio(this, uiSettings.getState().getAlphaModeRatio());
      }
    }

    // this prevents annoying flick
    // todo: do we really need this?
    paint(GraphicsUtil.safelyGetGraphics(this));

    ApplicationManager.getApplication().getMessageBus().connect(disposable).subscribe(UISettingsListener.TOPIC, uiSettingsListener);
  }

  @Override
  public void dispose(){
    if (ScreenUtil.isStandardAddRemoveNotify(getParent())) {
      delayAlarm.dispose();
      Disposer.dispose(disposable);
    }
    else if (isShowing()) {
      SwingUtilities.invokeLater(() -> show());
    }

    super.dispose();
    myDisposed = true;
  }

  @Override
  public boolean isWindowDisposed() {
    return myDisposed;
  }

  @Override
  public @NotNull Window getWindow() {
    return this;
  }

  @Override
  public @NotNull String getId() {
    return myDecorator.getToolWindowId();
  }

  @Override
  public void setLocationRelativeTo(Component c) {
    super.setLocationRelativeTo(c);
    myBoundsHelper.setBounds(getBounds());
  }

  @Override
  public @NotNull Rectangle getVisibleWindowBounds() {
    var result = getBounds();
    JBInsets.removeFrom(result, ToolWindowExternalDecoratorKt.getInvisibleInsets(this));
    return result;
  }

  @Override
  public void setVisibleWindowBounds(@NotNull Rectangle r) {
    myBoundsHelper.setBounds(r);
    var newBounds = new Rectangle(r);
    JBInsets.addTo(newBounds, ToolWindowExternalDecoratorKt.getInvisibleInsets(this));
    super.setBounds(newBounds);
  }

  @Override
  public @NotNull ToolWindowType getToolWindowType() {
    return ToolWindowType.FLOATING;
  }

  @Override
  @ApiStatus.Internal
  public void apply(@NotNull WindowInfo info) {
    LOG.assertTrue(info.getType() == ToolWindowType.FLOATING);
    myInfo = info;
    applyBounds(info);
    applyAlphaMode(info);
  }

  private void applyBounds(WindowInfo info) {
    Rectangle bounds = info.getFloatingBounds();
    if (bounds != null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Applying floating tool window " + info.getId() + " bounds from window info: " + bounds);
      }
      setVisibleWindowBounds(bounds);
    }
    else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Window " + info.getId() + " info has no floating bounds, not applying anything");
      }
    }
  }

  private void applyAlphaMode(@NotNull WindowInfo info) {
    UISettings uiSettings = UISettings.getInstance();
    if (!uiSettings.getState().getEnableAlphaMode() || !isShowing() || !isDisplayable()) {
      return;
    }

    delayAlarm.cancel();
    if (info.isActiveOnStart()) {
      // make window non transparent
      myFrameTicker.cancelAllRequests();
      myStartRatio = getCurrentAlphaRatio();
      if (myCurrentFrame > 0) {
        myCurrentFrame = TOTAL_FRAME_COUNT - myCurrentFrame;
      }
      myEndRatio = .0f;
      myFrameTicker.addRequest(myAnimator, DELAY);
    }
    else {
      // make window transparent
      delayAlarm.request(uiSettings.getState().getAlphaModeDelay(), () -> {
        myFrameTicker.cancelAllRequests();
        myStartRatio = getCurrentAlphaRatio();
        if (myCurrentFrame > 0) {
          myCurrentFrame = TOTAL_FRAME_COUNT - myCurrentFrame;
        }
        myEndRatio = uiSettings.getState().getAlphaModeRatio();
        myFrameTicker.addRequest(myAnimator, DELAY);
      });
    }
  }

  private float getCurrentAlphaRatio(){
    float delta=(myEndRatio-myStartRatio)/(float)TOTAL_FRAME_COUNT;
    if(myStartRatio>myEndRatio){ // dialog is becoming non transparent quicker
      delta*=2;
    }
    final float ratio=myStartRatio+(float)myCurrentFrame*delta;
    return MathUtil.clamp(ratio, .0f, 1.0f);
  }

  private final class BorderItem extends JPanel {
    private static final int RESIZER_WIDTH = 10;

    private final int myAnchor;
    private int myMotionMask;
    private Point myLastPoint;
    private boolean myDragging;

    BorderItem(int anchor) {
      myAnchor = anchor;
      enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    @Override
    protected void processMouseMotionEvent(final MouseEvent e){
      super.processMouseMotionEvent(e);
      if(MouseEvent.MOUSE_DRAGGED==e.getID() && myLastPoint != null){
        final Point newPoint=e.getPoint();
        SwingUtilities.convertPointToScreen(newPoint,this);
        Rectangle screenBounds = WindowManagerEx.getInstanceEx().getScreenBounds();
        int screenMaxX = screenBounds.x + screenBounds.width;
        int screenMaxY = screenBounds.y + screenBounds.height;

        newPoint.x = MathUtil.clamp(newPoint.x, screenBounds.x, screenMaxX);
        newPoint.y = MathUtil.clamp(newPoint.y, screenBounds.y, screenMaxY);

        final Rectangle oldBounds=FloatingDecorator.this.getBounds();
        final Rectangle newBounds=new Rectangle(oldBounds);

        if((myMotionMask&ANCHOR_TOP)>0){
          newPoint.y=Math.min(newPoint.y,oldBounds.y+oldBounds.height-2*DIVIDER_WIDTH);
          if(newPoint.y<screenBounds.y+DIVIDER_WIDTH){
            newPoint.y=screenBounds.y;
          }
          final Point offset=new Point(newPoint.x-myLastPoint.x,newPoint.y-myLastPoint.y);
          newBounds.y=oldBounds.y+offset.y;
          newBounds.height=oldBounds.height-offset.y;
        }
        if((myMotionMask&ANCHOR_LEFT)>0){
          newPoint.x=Math.min(newPoint.x,oldBounds.x+oldBounds.width-2*DIVIDER_WIDTH);
          if(newPoint.x<screenBounds.x+DIVIDER_WIDTH){
            newPoint.x=screenBounds.x;
          }
          final Point offset=new Point(newPoint.x-myLastPoint.x,newPoint.y-myLastPoint.y);
          newBounds.x=oldBounds.x+offset.x;
          newBounds.width=oldBounds.width-offset.x;
        }
        if((myMotionMask&ANCHOR_BOTTOM)>0){
          newPoint.y=Math.max(newPoint.y,oldBounds.y+2*DIVIDER_WIDTH);
          if (newPoint.y > screenMaxY - DIVIDER_WIDTH) {
            newPoint.y = screenMaxY;
          }
          final Point offset=new Point(newPoint.x-myLastPoint.x,newPoint.y-myLastPoint.y);
          newBounds.height=oldBounds.height+offset.y;
        }
        if((myMotionMask&ANCHOR_RIGHT)>0){
          newPoint.x=Math.max(newPoint.x,oldBounds.x+2*DIVIDER_WIDTH);
          if (newPoint.x > screenMaxX - DIVIDER_WIDTH) {
            newPoint.x = screenMaxX;
          }
          final Point offset=new Point(newPoint.x-myLastPoint.x,newPoint.y-myLastPoint.y);
          newBounds.width=oldBounds.width+offset.x;
        }
        // It's much better to resize frame this way then via Component.setBounds() method.
        // Component.setBounds() method cause annoying repainting and blinking.
        //FloatingDecorator.this.getPeer().setBounds(newBounds.x,newBounds.y,newBounds.width,newBounds.height, 0);
        FloatingDecorator.this.setBounds(newBounds.x,newBounds.y,newBounds.width,newBounds.height);

        myLastPoint=newPoint;
      } else if(e.getID()==MouseEvent.MOUSE_MOVED){
        if(!myDragging){
          setMotionMask(e.getPoint());
        }
      }
    }

    @Override
    protected void processMouseEvent(final MouseEvent e){
      super.processMouseEvent(e);
      switch (e.getID()) {
        case MouseEvent.MOUSE_PRESSED -> {
          myLastPoint = e.getPoint();
          SwingUtilities.convertPointToScreen(myLastPoint, this);
          setMotionMask(e.getPoint());
          myDragging = true;
        }
        case MouseEvent.MOUSE_RELEASED -> {
          FloatingDecorator.this.validate();
          FloatingDecorator.this.repaint();
          myDragging = false;
        }
        case MouseEvent.MOUSE_ENTERED -> {
          if (!myDragging) {
            setMotionMask(e.getPoint());
          }
        }
      }
    }

    private void setMotionMask(final Point p){
      myMotionMask=myAnchor;
      if(ANCHOR_TOP==myAnchor||ANCHOR_BOTTOM==myAnchor){
        if(p.getX()<RESIZER_WIDTH){
          myMotionMask|=ANCHOR_LEFT;
        } else if(p.getX()>getWidth()-RESIZER_WIDTH){
          myMotionMask|=ANCHOR_RIGHT;
        }
      } else{
        if(p.getY()<RESIZER_WIDTH){
          myMotionMask|=ANCHOR_TOP;
        } else if(p.getY()>getHeight()-RESIZER_WIDTH){
          myMotionMask|=ANCHOR_BOTTOM;
        }
      }
      if(myMotionMask==ANCHOR_TOP){
        setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
      } else if(myMotionMask==(ANCHOR_TOP|ANCHOR_LEFT)){
        setCursor(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR));
      } else if(myMotionMask==ANCHOR_LEFT){
        setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
      } else if(myMotionMask==(ANCHOR_LEFT|ANCHOR_BOTTOM)){
        setCursor(Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR));
      } else if(myMotionMask==ANCHOR_BOTTOM){
        setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
      } else if(myMotionMask==(ANCHOR_BOTTOM|ANCHOR_RIGHT)){
        setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
      } else if(myMotionMask==ANCHOR_RIGHT){
        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
      }
      else if (myMotionMask == (ANCHOR_RIGHT | ANCHOR_TOP)) {
        setCursor(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR));
      }
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension d = super.getPreferredSize();
      if (ANCHOR_TOP == myAnchor || ANCHOR_BOTTOM == myAnchor) {
        //noinspection SuspiciousNameCombination
        d.height = DIVIDER_WIDTH;
      }
      else {
        d.width = DIVIDER_WIDTH;
      }
      return d;
    }

    @Override
    public void paint(final Graphics g) {
      super.paint(g);
      final JBColor lightGray = new JBColor(JBColor.LIGHT_GRAY, Gray._95);
      final JBColor gray = new JBColor(JBColor.GRAY, Gray._95);
      if (ANCHOR_TOP == myAnchor) {
        g.setColor(lightGray);
        LinePainter2D.paint((Graphics2D)g, 0, 0, getWidth() - 1, 0);
        LinePainter2D.paint((Graphics2D)g, 0, 0, 0, getHeight() - 1);
        g.setColor(JBColor.GRAY);
        LinePainter2D.paint((Graphics2D)g, getWidth() - 1, 0, getWidth() - 1, getHeight() - 1);
      }
      else if (ANCHOR_LEFT == myAnchor) {
        g.setColor(lightGray);
        LinePainter2D.paint((Graphics2D)g, 0, 0, 0, getHeight() - 1);
      }
      else {
        if (ANCHOR_BOTTOM == myAnchor) {
          g.setColor(lightGray);
          LinePainter2D.paint((Graphics2D)g, 0, 0, 0, getHeight() - 1);
          g.setColor(gray);
          LinePainter2D.paint((Graphics2D)g, 0, getHeight() - 1, getWidth() - 1, getHeight() - 1);
        }
        else { // RIGHT
          g.setColor(gray);
        }
        LinePainter2D.paint((Graphics2D)g, getWidth() - 1, 0, getWidth() - 1, getHeight() - 1);
      }
    }
  }

  private final class MyAnimator implements Runnable {
    @Override
    public void run() {
      if (isDisplayable() && isShowing()) {
        WindowManager.getInstance().setAlphaModeRatio(FloatingDecorator.this, getCurrentAlphaRatio());
      }
      if (myCurrentFrame < TOTAL_FRAME_COUNT) {
        myCurrentFrame++;
        myFrameTicker.addRequest(myAnimator, DELAY);
      }
      else {
        myFrameTicker.cancelAllRequests();
      }
    }
  }

  private final class MyUISettingsListener implements UISettingsListener {
    @Override
    public void uiSettingsChanged(@NotNull UISettings uiSettings) {
      LOG.assertTrue(isDisplayable());
      LOG.assertTrue(isShowing());
      WindowManager windowManager = WindowManager.getInstance();
      delayAlarm.cancel();
      if (uiSettings.getState().getEnableAlphaMode()) {
        if (!isActive()) {
          windowManager.setAlphaModeEnabled(FloatingDecorator.this, true);
          windowManager.setAlphaModeRatio(FloatingDecorator.this, uiSettings.getState().getAlphaModeRatio());
        }
      }
      else {
        windowManager.setAlphaModeEnabled(FloatingDecorator.this, false);
      }
    }
  }

  @SuppressWarnings("deprecation") // overridden for logging purposes because all other bounds-affecting methods delegate to it
  @Override
  public void reshape(int x, int y, int width, int height) {
    // reshape() called from a superclass constructor, so we need this:
    //noinspection ConstantValue
    if (myDecorator == null) return;
    if (LOG.isTraceEnabled()) {
      LOG.trace(new Throwable("FloatingDecorator " + myDecorator.toolWindow.getId() +
                              " bounds changed to " + x + ", " + y + ", " + width + ", " + height));
    }
    super.reshape(x, y, width, height);
  }

  @Override
  public @NotNull Logger log() {
    return LOG;
  }
}
