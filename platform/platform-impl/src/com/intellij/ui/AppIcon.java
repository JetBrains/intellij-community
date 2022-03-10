// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.AppIconScheme;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.openapi.wm.impl.X11UiUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.*;
import com.sun.jna.platform.win32.WinDef;
import org.apache.commons.imaging.common.BinaryOutputStream;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The class must be accessed only from EDT.
 */
public abstract class AppIcon {
  private static final Logger LOG = Logger.getInstance(AppIcon.class);

  private static AppIcon ourIcon;

  @NotNull
  public static AppIcon getInstance() {
    if (ourIcon == null) {
      if (GraphicsEnvironment.isHeadless() || GraphicsUtil.isProjectorEnvironment()) {
        ourIcon = new EmptyIcon();
      }
      else if (SystemInfoRt.isMac) {
        ourIcon = new MacAppIcon();
      }
      else if (SystemInfoRt.isXWindow) {
        ourIcon = new XAppIcon();
      }
      else if (SystemInfoRt.isWindows && JnaLoader.isLoaded()) {
        ourIcon = new Win7AppIcon();
      }
      else {
        ourIcon = new EmptyIcon();
      }
    }

    return ourIcon;
  }

  public abstract boolean setProgress(Project project, @NonNls Object processId, AppIconScheme.Progress scheme, double value, boolean isOk);

  public abstract boolean hideProgress(Project project, @NonNls Object processId);

  public abstract void setErrorBadge(Project project, String text);

  public abstract void setOkBadge(@Nullable Project project, boolean visible);

  public abstract void requestAttention(@Nullable Project project, boolean critical);

  /**
   * This method requests the OS to activate the specified application window.
   * This might cause the window to steal focus from another (currently active) application, which is generally not considered acceptable.
   * So it should be used only in special cases, when we know that the user definitely expects such behaviour.
   * In most cases, requesting focus in the target window via the standard AWT mechanism and {@link #requestAttention(Project, boolean)}
   * should be used instead, to request user attention but not switch the focus to it automatically.
   * <p>
   * This method might resort to requesting user attention to a target window if focus stealing is not supported by the OS
   * (this is the case on Windows, where focus stealing can only be enabled using {@link WinFocusStealer}).
   */
  public void requestFocus(IdeFrame frame) {
    requestFocus(frame == null ? null : SwingUtilities.getWindowAncestor(frame.getComponent()));
  }

  protected void requestFocus(@Nullable Window window) {
  }

  public void requestFocus() {
  }

  private static abstract class BaseIcon extends AppIcon {
    private ApplicationActivationListener myAppListener;
    protected Object myCurrentProcessId;
    protected double myLastValue;

    @Override
    public final boolean setProgress(Project project, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      if (!isAppActive() && isProgressAppIconEnabled() && (myCurrentProcessId == null || myCurrentProcessId.equals(processId))) {
        return _setProgress(getIdeFrame(project), processId, scheme, value, isOk);
      }
      else {
        return false;
      }
    }

    @Override
    public final boolean hideProgress(Project project, Object processId) {
      if (isProgressAppIconEnabled()) {
        return _hideProgress(getIdeFrame(project), processId);
      }
      else {
        return false;
      }
    }

    @Override
    public final void setErrorBadge(Project project, String text) {
      if (!isAppActive() && Registry.is("ide.appIcon.badge")) {
        _setOkBadge(getIdeFrame(project), false);
        _setTextBadge(getIdeFrame(project), text);
      }
    }

    @Override
    public final void setOkBadge(@Nullable Project project, boolean visible) {
      if (!isAppActive() && Registry.is("ide.appIcon.badge")) {
        _setTextBadge(getIdeFrame(project), null);
        _setOkBadge(getIdeFrame(project), visible);
      }
    }

    @Override
    public final void requestAttention(@Nullable Project project, boolean critical) {
      if (!isAppActive() && Registry.is("ide.appIcon.requestAttention")) {
        _requestAttention(getIdeFrame(project), critical);
      }
    }

    public abstract boolean _setProgress(@Nullable JFrame frame, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk);

    public abstract boolean _hideProgress(@Nullable JFrame frame, Object processId);

    public abstract void _setTextBadge(@Nullable JFrame frame, String text);

    public abstract void _setOkBadge(@Nullable JFrame frame, boolean visible);

    public abstract void _requestAttention(@Nullable JFrame frame, boolean critical);

    private static @Nullable JFrame getIdeFrame(@Nullable Project project) {
      return WindowManager.getInstance().getFrame(project);
    }

    private boolean isAppActive() {
      Application app = ApplicationManager.getApplication();
      if (app == null || myAppListener != null) {
        return app != null && app.isActive();
      }

      myAppListener = new ApplicationActivationListener() {
        @Override
        public void applicationActivated(@NotNull IdeFrame ideFrame) {
          JFrame frame;
          if (ideFrame instanceof JFrame) {
            frame = (JFrame)ideFrame;
          }
          else {
            frame = ((ProjectFrameHelper)ideFrame).getFrame();
          }

          if (isProgressAppIconEnabled()) {
            _hideProgress(frame, myCurrentProcessId);
          }
          _setOkBadge(frame, false);
          _setTextBadge(frame, null);
        }
      };
      app.getMessageBus().connect().subscribe(ApplicationActivationListener.TOPIC, myAppListener);
      return app.isActive();
    }
  }

  private static boolean isProgressAppIconEnabled() {
    return SystemProperties.getBooleanProperty("ide.appIcon.progress", true);
  }

  @SuppressWarnings("UseJBColor")
  static final class MacAppIcon extends BaseIcon {
    private BufferedImage myAppImage;
    private final Map<Object, Pair<BufferedImage, Graphics2D>> myProgressImagesCache = new HashMap<>();

    private BufferedImage getAppImage() {
      EDT.assertIsEdt();

      try {
        if (myAppImage != null) return myAppImage;

        Image appImage = (Image)getAppMethod("getDockIconImage").invoke(getApp());
        if (appImage == null) return null;

        // [tav] expecting two resolution variants for the dock icon: 128x128, 256x256
        appImage = MultiResolutionImageProvider.getMaxSizeResolutionVariant(appImage);
        myAppImage = ImageUtil.toBufferedImage(appImage);
      }
      catch (NoSuchMethodException e) {
        return null;
      }
      catch (Exception e) {
        LOG.error(e);
      }

      return myAppImage;
    }

    @Override
    public void _setTextBadge(@Nullable JFrame frame, String text) {
      EDT.assertIsEdt();

      try {
        getAppMethod("setDockIconBadge", String.class).invoke(getApp(), text);
      }
      catch (NoSuchMethodException ignored) { }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    @Override
    protected void requestFocus(@Nullable Window window) {
      if (window != null) {
        window.toFront();
      }
      requestFocus();
    }

    @Override
    public void requestFocus() {
      EDT.assertIsEdt();

      try {
        getAppMethod("requestForeground", boolean.class).invoke(getApp(), true);
      }
      catch (NoSuchMethodException ignored) { }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    @Override
    public void _requestAttention(@Nullable JFrame frame, boolean critical) {
      EDT.assertIsEdt();

      try {
        getAppMethod("requestUserAttention", boolean.class).invoke(getApp(), critical);
      }
      catch (NoSuchMethodException ignored) { }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    @Override
    public boolean _hideProgress(@Nullable JFrame frame, Object processId) {
      EDT.assertIsEdt();

      if (myCurrentProcessId != null && !myCurrentProcessId.equals(processId)) {
        return false;
      }

      BufferedImage appImage = getAppImage();
      if (appImage == null) {
        return false;
      }

      setDockIcon(appImage);
      myProgressImagesCache.remove(myCurrentProcessId);
      myCurrentProcessId = null;
      myLastValue = 0;
      return true;
    }

    @Override
    public void _setOkBadge(@Nullable JFrame frame, boolean visible) {
      EDT.assertIsEdt();

      BufferedImage appImage = getAppImage();
      if (appImage == null) {
        return;
      }

      Pair<BufferedImage, Graphics2D> img = createAppImage(appImage);

      if (visible) {
        Icon okIcon = AllIcons.Mac.AppIconOk512;
        int w = img.first.getWidth();
        if (w != 128) {
          okIcon = IconUtil.scale(okIcon, frame != null ? frame.getRootPane() : null, w / 128f);
        }

        int x = w - okIcon.getIconWidth();
        int y = 0;

        okIcon.paintIcon(JOptionPane.getRootFrame(), img.second, x, y);
      }

      setDockIcon(img.first);
    }

    // white 80% transparent
    private static final Color PROGRESS_BACKGROUND_COLOR = new Color(255, 255, 255, 217);
    private static final Color PROGRESS_OUTLINE_COLOR = new Color(140, 139, 140);

    @Override
    public boolean _setProgress(@Nullable JFrame frame, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      EDT.assertIsEdt();

      BufferedImage appImage = getAppImage();
      if (appImage == null) return false;

      myCurrentProcessId = processId;

      if (myLastValue > value) return true;

      if (Math.abs(myLastValue - value) < 0.02d) return true;

      try {
        double progressHeight = (myAppImage.getHeight() * 0.13);
        double xInset = (myAppImage.getWidth() * 0.05);
        double yInset = (myAppImage.getHeight() * 0.15);

        final double width = myAppImage.getWidth() - xInset * 2;
        final double y = myAppImage.getHeight() - progressHeight - yInset;

        Area borderArea = new Area( new RoundRectangle2D.Double(
          xInset - 1, y - 1, width + 2, progressHeight + 2,
          (progressHeight + 2), (progressHeight + 2
          )));

        Area backgroundArea = new Area(new Rectangle2D.Double(xInset, y, width, progressHeight));

        backgroundArea.intersect(borderArea);

        Area progressArea = new Area(new Rectangle2D.Double(xInset + 1, y + 1,(width - 2) * value, progressHeight - 1));

        progressArea.intersect(borderArea);

        Pair<BufferedImage, Graphics2D> appImg = myProgressImagesCache.get(myCurrentProcessId);
        if (appImg == null) myProgressImagesCache.put(myCurrentProcessId, appImg = createAppImage(appImage));

        Graphics2D g2d = appImg.second;
        g2d.setColor(PROGRESS_BACKGROUND_COLOR);
        g2d.fill(backgroundArea);
        g2d.setColor(isOk ? scheme.getOkColor() : scheme.getErrorColor());
        g2d.fill(progressArea);
        g2d.setColor(PROGRESS_OUTLINE_COLOR);
        g2d.draw(backgroundArea);
        g2d.draw(borderArea);

        setDockIcon(appImg.first);

        myLastValue = value;
      }
      catch (Exception e) {
        LOG.error(e);
      }
      finally {
        myCurrentProcessId = null;
      }

      return true;
    }

    private static Pair<BufferedImage, Graphics2D> createAppImage(BufferedImage appImage) {
      @SuppressWarnings("UndesirableClassUsage") BufferedImage current = new BufferedImage(appImage.getWidth(), appImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = current.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      StartupUiUtil.drawImage(g, appImage, 0, 0, null);
      return new Pair<>(current, g);
    }

    static void setDockIcon(BufferedImage image) {
      try {
        getAppMethod("setDockIconImage", Image.class).invoke(getApp(), image);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    private static Method getAppMethod(String name, Class<?>... args) throws NoSuchMethodException, ClassNotFoundException {
      return getAppClass().getMethod(name, args);
    }

    private static Object getApp() throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, IllegalAccessException {
      return getAppClass().getMethod("getApplication").invoke(null);
    }

    private static Class<?> getAppClass() throws ClassNotFoundException {
      return Class.forName("com.apple.eawt.Application");
    }
  }

  @SuppressWarnings("UseJBColor")
  private static final class Win7AppIcon extends BaseIcon {
    @Override
    public boolean _setProgress(@Nullable JFrame frame, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      EDT.assertIsEdt();

      myCurrentProcessId = processId;

      if (Math.abs(myLastValue - value) < 0.02d) {
        return true;
      }

      try {
        if (isValid(frame)) {
          Win7TaskBar.setProgress(frame, value, isOk);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }

      myLastValue = value;
      myCurrentProcessId = null;
      return true;
    }

    @Override
    public boolean _hideProgress(@Nullable JFrame frame, Object processId) {
      EDT.assertIsEdt();
      if (myCurrentProcessId != null && !myCurrentProcessId.equals(processId)) {
        return false;
      }

      try {
        if (isValid(frame)) {
          Win7TaskBar.hideProgress(frame);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }

      myCurrentProcessId = null;
      myLastValue = 0;
      return true;
    }

    private static byte[] writeTransparentIco(BufferedImage src)
      throws IOException {

      LOG.assertTrue(BufferedImage.TYPE_INT_ARGB == src.getType() || BufferedImage.TYPE_4BYTE_ABGR == src.getType());

      int bitCount = 32;

      try (ByteArrayOutputStream os = new ByteArrayOutputStream();
           BinaryOutputStream bos = new BinaryOutputStream(os, ByteOrder.LITTLE_ENDIAN)) {
        int scan_line_size = (bitCount * src.getWidth() + 7) / 8;
        if ((scan_line_size % 4) != 0)
          scan_line_size += 4 - (scan_line_size % 4); // pad scan line to 4 byte size.
        int t_scan_line_size = (src.getWidth() + 7) / 8;
        if ((t_scan_line_size % 4) != 0)
          t_scan_line_size += 4 - (t_scan_line_size % 4); // pad scan line to 4 byte size.
        int imageSize = 40 + src.getHeight() * scan_line_size + src.getHeight() * t_scan_line_size;

        // ICONDIR
        bos.write2Bytes(0); // reserved
        bos.write2Bytes(1); // 1=ICO, 2=CUR
        bos.write2Bytes(1); // count

        // ICONDIRENTRY
        int iconDirEntryWidth = src.getWidth();
        int iconDirEntryHeight = src.getHeight();
        if (iconDirEntryWidth > 255 || iconDirEntryHeight > 255) {
          iconDirEntryWidth = 0;
          iconDirEntryHeight = 0;
        }
        bos.write(iconDirEntryWidth);
        bos.write(iconDirEntryHeight);
        bos.write(0);
        bos.write(0); // reserved
        bos.write2Bytes(1); // color planes
        bos.write2Bytes(bitCount);
        bos.write4Bytes(imageSize);
        bos.write4Bytes(22); // image offset

        // BITMAPINFOHEADER
        bos.write4Bytes(40); // size
        bos.write4Bytes(src.getWidth());
        bos.write4Bytes(2 * src.getHeight());
        bos.write2Bytes(1); // planes
        bos.write2Bytes(bitCount);
        bos.write4Bytes(0); // compression
        bos.write4Bytes(0); // image size
        bos.write4Bytes(0); // x pixels per meter
        bos.write4Bytes(0); // y pixels per meter
        bos.write4Bytes(0); // colors used, 0 = (1 << bitCount) (ignored)
        bos.write4Bytes(0); // colors important

        int bit_cache = 0;
        int bits_in_cache = 0;
        int row_padding = scan_line_size - (bitCount * src.getWidth() + 7) / 8;
        for (int y = src.getHeight() - 1; y >= 0; y--) {
          for (int x = 0; x < src.getWidth(); x++) {
            int argb = src.getRGB(x, y);

            bos.write(0xff & argb);
            bos.write(0xff & (argb >> 8));
            bos.write(0xff & (argb >> 16));
            bos.write(0xff & (argb >> 24));
          }

          for (int x = 0; x < row_padding; x++)
            bos.write(0);
        }

        int t_row_padding = t_scan_line_size - (src.getWidth() + 7) / 8;
        for (int y = src.getHeight() - 1; y >= 0; y--) {
          for (int x = 0; x < src.getWidth(); x++) {
            int argb = src.getRGB(x, y);
            int alpha = 0xff & (argb >> 24);
            bit_cache <<= 1;
            if (alpha == 0)
              bit_cache |= 1;
            bits_in_cache++;
            if (bits_in_cache >= 8) {
              bos.write(0xff & bit_cache);
              bit_cache = 0;
              bits_in_cache = 0;
            }
          }

          if (bits_in_cache > 0) {
            bit_cache <<= (8 - bits_in_cache);
            bos.write(0xff & bit_cache);
            bit_cache = 0;
            bits_in_cache = 0;
          }

          for (int x = 0; x < t_row_padding; x++)
            bos.write(0);
        }

        return os.toByteArray();
      }
    }

    private static final Color errorBadgeShadowColor = new Color(0, 0, 0, 102);
    private static final Color errorBadgeMainColor = new Color(255, 98, 89);
    private static final Color errorBadgeTextBackgroundColor = new Color(0, 0, 0, 39);

    @Override
    public void _setTextBadge(@Nullable JFrame frame, String text) {
      EDT.assertIsEdt();
      if (!isValid(frame)) {
        return;
      }

      WinDef.HICON icon = null;

      if (text != null) {
        try {
          int size = 16;
          BufferedImage image = UIUtil.createImage(frame.getRootPane(), size, size, BufferedImage.TYPE_INT_ARGB);
          Graphics2D g = image.createGraphics();

          int shadowRadius = 16;
          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g.setPaint(errorBadgeShadowColor);
          g.fillRoundRect(0, 0, shadowRadius, shadowRadius, size, size);

          int mainRadius = 14;
          g.setPaint(errorBadgeMainColor);
          g.fillRoundRect(size / 2 - mainRadius / 2, size / 2 - mainRadius / 2, mainRadius, mainRadius, size, size);

          Font font = g.getFont();
          g.setFont(new Font(font.getName(), Font.BOLD, 9));
          FontMetrics fontMetrics = g.getFontMetrics();

          int textWidth = fontMetrics.stringWidth(text);
          int textHeight = UIUtil.getHighestGlyphHeight(text, font, g);

          g.setPaint(errorBadgeTextBackgroundColor);
          g.fillOval(size / 2 - textWidth / 2, size / 2 - textHeight / 2, textWidth, textHeight);

          g.setColor(Color.white);
          g.drawString(text, size / 2 - textWidth / 2, size / 2 - fontMetrics.getHeight() / 2 + fontMetrics.getAscent());

          byte[] bytes = writeTransparentIco(image);
          icon = Win7TaskBar.createIcon(bytes);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }

      try {
        Win7TaskBar.setOverlayIcon(frame, icon, icon != null);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    private WinDef.HICON myOkIcon;

    @Override
    public void _setOkBadge(@Nullable JFrame frame, boolean visible) {
      EDT.assertIsEdt();
      if (!isValid(frame)) {
        return;
      }

      WinDef.HICON icon = null;

      if (visible) {
        synchronized (Win7AppIcon.class) {
          if (myOkIcon == null) {
            try {
              BufferedImage image = ImageIO.read(Objects.requireNonNull(AppIcon.class.getResourceAsStream("/mac/appIconOk512.png")));
              byte[] bytes = writeTransparentIco(image);
              myOkIcon = Win7TaskBar.createIcon(bytes);
            }
            catch (Throwable e) {
              LOG.error(e);
              myOkIcon = null;
            }
          }

          icon = myOkIcon;
        }
      }

      try {
        Win7TaskBar.setOverlayIcon(frame, icon, false);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    @Override
    public void _requestAttention(@Nullable JFrame frame, boolean critical) {
      EDT.assertIsEdt();
      try {
        if (isValid(frame)) {
          Win7TaskBar.attention(frame);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    @Override
    protected void requestFocus(@Nullable Window window) {
      if (window != null) {
        try {
          // This is required for the focus stealing mechanism to work reliably;
          // see WinFocusStealer.setFocusStealingEnabled javadoc for details
          Thread.sleep(Registry.intValue("win.request.focus.delay.ms"));
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        window.toFront();
      }
    }

    private static boolean isValid(@Nullable JFrame frame) {
      return frame != null && frame.isDisplayable();
    }
  }

  private static final class XAppIcon extends BaseIcon {
    @Override
    public boolean _setProgress(@Nullable JFrame frame, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      return false;
    }

    @Override
    public boolean _hideProgress(@Nullable JFrame frame, Object processId) {
      return false;
    }

    @Override
    public void _setTextBadge(@Nullable JFrame frame, String text) {}

    @Override
    public void _setOkBadge(@Nullable JFrame frame, boolean visible) {}

    @Override
    public void _requestAttention(@Nullable JFrame frame, boolean critical) {
      if (frame != null) X11UiUtil.requestAttention(frame);
    }

    @Override
    public void requestFocus(Window window) {
      if (window != null) X11UiUtil.activate(window);
    }
  }

  private static final class EmptyIcon extends AppIcon {
    @Override
    public boolean setProgress(Project project, Object processId, AppIconScheme.Progress scheme, double value, boolean isOk) {
      return false;
    }

    @Override
    public boolean hideProgress(Project project, Object processId) {
      return false;
    }

    @Override
    public void setErrorBadge(Project project, String text) { }

    @Override
    public void setOkBadge(Project project, boolean visible) { }

    @Override
    public void requestAttention(@Nullable Project project, boolean critical) { }
  }
}
