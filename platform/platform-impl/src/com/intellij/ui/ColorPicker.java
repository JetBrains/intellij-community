// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.colorpicker.*;
import com.intellij.ui.picker.ColorListener;
import com.intellij.ui.picker.ColorPickerPopupCloseListener;
import com.intellij.ui.picker.ColorPipette;
import com.intellij.ui.picker.ColorPipetteBase;
import com.intellij.ui.picker.MacColorPipette;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TimerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.MemoryImageSource;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ColorPicker extends JPanel implements ColorListener, DocumentListener {
  private static final String COLOR_CHOOSER_COLORS_KEY = "ColorChooser.RecentColors";
  private static final String HSB_PROPERTY = "color.picker.is.hsb";

  private Color myColor;
  private ColorPreviewComponent myPreviewComponent;
  private final ColorWheelPanel myColorWheelPanel;
  private final JTextField myRed;
  private final JTextField myGreen;
  private final JTextField myBlue;
  private final JTextField myHex;
  private final Alarm myUpdateQueue;
  private final List<? extends ColorPickerListener> myExternalListeners;

  private RecentColorsComponent myRecentColorsComponent;
  @Nullable
  private final ColorPipette myPicker;
  private final JLabel myR = new JLabel(IdeBundle.message("colorpicker.label.red"));
  private final JLabel myG = new JLabel(IdeBundle.message("colorpicker.label.green"));
  private final JLabel myB = new JLabel(IdeBundle.message("colorpicker.label.blue"));
  private final JLabel myR_after = new JLabel(" ");
  private final JLabel myG_after = new JLabel(" ");
  private final JLabel myB_after = new JLabel(" "); // " " is here because empty text would cause unfortunate traversal order (WEB-43164)
  private final JComboBox<String> myFormat = new ComboBox<>(new String[] { IdeBundle.message("colorpicker.format.rgb"),
                                                                           IdeBundle.message("colorpicker.format.hsb")}) {
    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      UIManager.LookAndFeelInfo info = LafManager.getInstance().getCurrentLookAndFeel();
      if (info != null && info.getName().contains("Windows"))
        size.width += 10;
      return size;
    }
  };

  public ColorPicker(@NotNull Disposable parent, @Nullable Color color, boolean enableOpacity) {
    this(parent, color, true, enableOpacity, Collections.emptyList(), false);
  }

  private ColorPicker(@NotNull Disposable parent,
                      @Nullable Color color,
                      boolean restoreColors, boolean enableOpacity,
                      List<? extends ColorPickerListener> listeners, boolean opacityInPercent) {
    myUpdateQueue = new Alarm(Alarm.ThreadToUse.SWING_THREAD, parent);
    myRed = createColorField(false);
    myGreen = createColorField(false);
    myBlue = createColorField(false);
    myHex = createColorField(true);
    setLayout(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
    myColorWheelPanel = new ColorWheelPanel(this, enableOpacity, opacityInPercent);

    myExternalListeners = listeners;
    myFormat.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PropertiesComponent.getInstance().setValue(HSB_PROPERTY, String.valueOf(!isRGBMode()), Boolean.FALSE.toString());
        myR.setText(IdeBundle.message(isRGBMode() ? "colorpicker.label.red" : "colorpicker.label.hue"));
        myR_after.setText(isRGBMode() ? " " : "\u00B0");
        myG.setText(IdeBundle.message(isRGBMode() ? "colorpicker.label.green" : "colorpicker.label.saturation"));
        myG_after.setText(isRGBMode() ? " " : "%");
        myB.setText(IdeBundle.message(isRGBMode() ? "colorpicker.label.blue" : "colorpicker.label.brightness"));
        myB_after.setText(isRGBMode() ? " " : "%"); // " " is here because empty text would cause unfortunate traversal order (WEB-43164)
        applyColor(myColor);
      }
    });

    myPicker = createPipette(new ColorListener() {
      @Override
      public void colorChanged(Color color, Object source) {
        setColor(color, source);
      }
    }, parent);
    try {
      add(buildTopPanel(true), BorderLayout.NORTH);
      add(myColorWheelPanel, BorderLayout.CENTER);

      myRecentColorsComponent = new RecentColorsComponent(new ColorListener() {
        @Override
        public void colorChanged(Color color, Object source) {
          setColor(color, source);
        }
      }, restoreColors);

      add(myRecentColorsComponent, BorderLayout.SOUTH);
    }
    catch (ParseException ignore) {
    }

    //noinspection UseJBColor
    Color c = ObjectUtils.notNull(color == null ? myRecentColorsComponent.getMostRecentColor() : color, Color.WHITE);
    setColor(c, this);

    setSize(300, 350);

    final boolean hsb = PropertiesComponent.getInstance().getBoolean(HSB_PROPERTY);
    if (hsb) {
      myFormat.setSelectedIndex(1);
    }
  }

  @Nullable
  public static RelativePoint bestLocationForColorPickerPopup(@Nullable Editor editor) {
    if (editor == null || editor.isDisposed()) {
      return null;
    }
    AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
    if (event instanceof MouseEvent) {
      Component component = ((MouseEvent)event).getComponent();
      Component clickComponent = SwingUtilities.getDeepestComponentAt(component, ((MouseEvent)event).getX(), ((MouseEvent)event).getY());
      if (clickComponent instanceof EditorGutter) {
        return null;
      }
    }
    VisualPosition visualPosition = editor.getCaretModel().getCurrentCaret().getVisualPosition();
    Point pointInEditor = editor.visualPositionToXY(new VisualPosition(visualPosition.line + 1, visualPosition.column));
    return new RelativePoint(editor.getContentComponent(),pointInEditor);
  }

  @Nullable
  private ColorPipette createPipette(@NotNull ColorListener colorListener, @NotNull Disposable parentDisposable) {
    if (ColorPipetteBase.canUseMacPipette()) {
      ColorPipette pipette = getPipetteIfAvailable(new MacColorPipette(this, colorListener), parentDisposable);
      if (pipette != null) {
        return pipette;
      }
    }
    return getPipetteIfAvailable(new DefaultColorPipette(this, colorListener), parentDisposable);
  }

  @Nullable
  private static ColorPipette getPipetteIfAvailable(@NotNull ColorPipette pipette, @NotNull Disposable parentDisposable) {
    if (pipette.isAvailable()) {
      Disposer.register(parentDisposable, pipette);
      return pipette;
    }
    else {
      Disposer.dispose(pipette);
      return null;
    }
  }

  private boolean isRGBMode() {
    return myFormat.getSelectedIndex() == 0;
  }

  private JTextField createColorField(boolean hex) {
    NumberDocument doc = new NumberDocument(hex);
    JTextField field = new JTextField("");
    field.setDocument(doc);

    doc.setSource(field);
    field.getDocument().addDocumentListener(this);
    field.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(final FocusEvent e) {
        field.selectAll();
      }

      @Override
      public void focusLost(FocusEvent event) {
        myUpdateQueue.cancelAllRequests();
        validateAndUpdatePreview(field);
      }
    });
    return field;
  }

  public JComponent getPreferredFocusedComponent() {
    return myHex;
  }

  private void setColor(Color color, Object src) {
    colorChanged(color, src);
    myColorWheelPanel.setColor(color, src);
  }

  public void appendRecentColor() {
    myRecentColorsComponent.appendColor(myColor);
  }

  public void saveRecentColors() {
    myRecentColorsComponent.saveColors();
  }

  public Color getColor() {
    if (myColorWheelPanel.myColorWheel.myOpacity == 255) {
      return myColor;
    } else {
      //noinspection UseJBColor
      return new Color(myColor.getRed(), myColor.getGreen(), myColor.getBlue(), myColorWheelPanel.myColorWheel.myOpacity);
    }
  }

  @Override
  public void insertUpdate(DocumentEvent e) {
    update(((NumberDocument)e.getDocument()).mySrc);
  }

  private void update(final JTextField src) {
    if (!src.hasFocus()) return;

    myUpdateQueue.cancelAllRequests();
    myUpdateQueue.addRequest(() -> validateAndUpdatePreview(src), 300);
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    update(((NumberDocument)e.getDocument()).mySrc);
  }

  @Override
  public void changedUpdate(DocumentEvent e) {
    // ignore
  }

  private void validateAndUpdatePreview(JTextField src) {
    boolean fromHex = src == myHex;
    Color color;
    if (fromHex) {
      Color c = ColorUtil.fromHex(myHex.getText(), null);
      color = c != null ? ColorUtil.toAlpha(c, myColorWheelPanel.myColorWheel.myOpacity) : null;
    } else {
      color = gatherRGB();
    }
    if (color != null) {
      if (myColorWheelPanel.myOpacityComponent != null) {
        color = ColorUtil.toAlpha(color, myColorWheelPanel.myOpacityComponent.getValue());
      }
      updatePreview(color, fromHex);
    }
  }

  private void updatePreview(Color color, boolean fromHex) {
    if (color != null && !color.equals(myColor)) {
      myColor = color;
      myPreviewComponent.setColor(color);
      myColorWheelPanel.setColor(color, fromHex ? myHex : null);


      if (fromHex) {
        applyColor(color);
      } else {
        applyColorToHEX(color);
      }

      fireColorChanged(color);
    }
  }

  @Override
  public void colorChanged(Color color, Object source) {
    if (color != null && !color.equals(myColor)) {
      myColor = color;

      applyColor(color);

      if (source != myHex) {
        applyColorToHEX(color);
      }
      myPreviewComponent.setColor(color);
      fireColorChanged(color);
    }
  }

  private void fireColorChanged(Color color) {
    if (myExternalListeners == null) return;
    for (ColorPickerListener listener : myExternalListeners) {
      listener.colorChanged(color);
    }
  }

  private void fireClosed(@Nullable Color color) {
    if (myExternalListeners == null) return;
    for (ColorPickerListener listener : myExternalListeners) {
      listener.closed(color);
    }
  }

  @Nullable
  private Color gatherRGB() {
    try {
      final int r = Integer.parseInt(myRed.getText());
      final int g = Integer.parseInt(myGreen.getText());
      final int b = Integer.parseInt(myBlue.getText());

      //noinspection UseJBColor
      return isRGBMode() ? new Color(r, g, b) : new Color(Color.HSBtoRGB(((float)r) / 360f, ((float)g) / 100f, ((float)b) / 100f));
    } catch (Exception ignore) {
    }
    return null;
  }

  private void applyColorToHEX(final Color c) {
    myHex.setText(String.format("%06X", (0xFFFFFF & c.getRGB())));
  }

  private void applyColorToRGB(final Color color) {
    myRed.setText(String.valueOf(color.getRed()));
    myGreen.setText(String.valueOf(color.getGreen()));
    myBlue.setText(String.valueOf(color.getBlue()));
  }

  private void applyColorToHSB(final Color c) {
    final float[] hbs = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    myRed.setText(String.valueOf(((int)(360f * hbs[0]))));
    myGreen.setText(String.valueOf(((int)(100f * hbs[1]))));
    myBlue.setText(String.valueOf(((int)(100f * hbs[2]))));
  }

  private void applyColor(final Color color) {
    if (isRGBMode()) {
      applyColorToRGB(color);
    } else {
      applyColorToHSB(color);
    }
  }

  @Nullable
  public static Color showDialog(@NotNull Component parent,
                                 @DialogTitle String caption,
                                 Color preselectedColor,
                                 boolean enableOpacity,
                                 List<? extends ColorPickerListener> listeners,
                                 boolean opacityInPercent) {
    final ColorPickerDialog dialog = new ColorPickerDialog(parent, caption, preselectedColor, enableOpacity, listeners, opacityInPercent);
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      return dialog.getColor();
    }

    return null;
  }

  /**
   * @deprecated this method doesn't support remote development. Replace with ColorChooserService.getInstance().showPopup
   */
  @Deprecated(forRemoval = true)
  public static void showColorPickerPopup(@Nullable Project project, @Nullable Color currentColor, @Nullable Editor editor, @NotNull ColorListener listener) {
    showColorPickerPopup(project, currentColor, listener, bestLocationForColorPickerPopup(editor), currentColor != null && currentColor.getAlpha() != 255);
  }

  /**
   * @deprecated this method doesn't support remote development. Replace with ColorChooserService.getInstance().showPopup
   */
  @Deprecated(forRemoval = true)
  public static void showColorPickerPopup(@Nullable Project project, @Nullable Color currentColor, @Nullable Editor editor, @NotNull ColorListener listener, boolean showAlpha) {
    showColorPickerPopup(project, currentColor, listener, bestLocationForColorPickerPopup(editor), showAlpha);
  }

  /**
   * @deprecated this method doesn't support remote development. Replace with ColorChooserService.getInstance().showPopup
   */
  @Deprecated(forRemoval = true)
  public static void showColorPickerPopup(@Nullable Project project, @Nullable Color currentColor, @Nullable Editor editor, @NotNull ColorListener listener, boolean showAlpha, boolean showAlphaAsPercent) {
    showColorPickerPopup(project, currentColor, listener, bestLocationForColorPickerPopup(editor), showAlpha, showAlphaAsPercent);
  }

  /**
   * @deprecated this method doesn't support remote development. Replace with ColorChooserService.getInstance().showPopup
   */
  @Deprecated(forRemoval = true)
  public static void showColorPickerPopup(@Nullable Project project, @Nullable Color currentColor, @NotNull ColorListener listener) {
    showColorPickerPopup(project, currentColor, listener, null);
  }

  /**
   * @deprecated this method doesn't support remote development. Replace with ColorChooserService.getInstance().showPopup
   */
  @Deprecated(forRemoval = true)
  public static void showColorPickerPopup(@Nullable Project project, @Nullable Color currentColor, @NotNull ColorListener listener, @Nullable RelativePoint location) {
    showColorPickerPopup(project, currentColor, listener, location, false);
  }

  /**
   * @deprecated this method doesn't support remote development. Replace with ColorChooserService.getInstance().showPopup
   */
  @Deprecated(forRemoval = true)
  public static void showColorPickerPopup(@Nullable final Project project, @Nullable Color currentColor, @NotNull final ColorListener listener, @Nullable RelativePoint location, boolean showAlpha) {
    showColorPickerPopup(project, currentColor, listener, location, showAlpha, false);
  }

  /**
   * @deprecated this method doesn't support remote development. Replace with ColorChooserService.getInstance().showPopup
   */
  @Deprecated(forRemoval = true)
  public static void showColorPickerPopup(@Nullable final Project project, @Nullable Color currentColor, @NotNull final ColorListener listener, @Nullable RelativePoint location, boolean showAlpha, boolean showAlphaAsPercent) {
    showColorPickerPopup(project, currentColor, listener, location, showAlpha, showAlphaAsPercent, null);
  }

  static void showColorPickerPopup(@Nullable final Project project,
                                   @Nullable Color currentColor,
                                   @Nullable final Editor editor,
                                   @NotNull final ColorListener listener,
                                   boolean showAlpha,
                                   boolean showAlphaAsPercent,
                                   @Nullable final ColorPickerPopupCloseListener popupCloseListener) {
    showColorPickerPopup(project, currentColor, listener, bestLocationForColorPickerPopup(editor), showAlpha, showAlphaAsPercent, popupCloseListener);
  }

  static void showColorPickerPopup(@Nullable final Project project,
                                   @Nullable Color currentColor,
                                   @NotNull final ColorListener listener,
                                   @Nullable RelativePoint location,
                                   boolean showAlpha,
                                   boolean showAlphaAsPercent,
                                   @Nullable final ColorPickerPopupCloseListener popupCloseListener) {
    if( !isEnoughSpaceToShowPopup() || !Registry.is("ide.new.color.picker")) {
      Color color = showDialog(IdeFocusManager.getGlobalInstance().getFocusOwner(), IdeBundle.message("dialog.title.choose.color"),
                               currentColor, showAlpha, null, showAlphaAsPercent);
      if (color != null) {
        listener.colorChanged(color, null);
      }
      return;
    }
    Ref<LightCalloutPopup> ref = Ref.create();

    ColorListener colorListener = new ColorListener() {
      final Object groupId = new Object();
      final Alarm alarm = new Alarm();

      @Override
      public void colorChanged(final Color color, final Object source) {
        Runnable apply = () -> CommandProcessor.getInstance().executeCommand(project,
                                                                             () -> listener.colorChanged(color, source),
                                                                             IdeBundle.message("command.name.apply.color"),
                                                                             groupId);
        alarm.cancelAllRequests();
        Runnable request = () -> ApplicationManager.getApplication().invokeLaterOnWriteThread(apply);
        if (source instanceof ColorPipetteButton && ((ColorPipetteButton)source).getCurrentState() == ColorPipetteButton.PipetteState.UPDATING) {
          alarm.addRequest(request, 150);
        } else {
          request.run();
        }
      }
    };

    List<Color> recentColors = RecentColorsComponent.getRecentColors();
    ColorPickerBuilder builder = new ColorPickerBuilder(showAlpha, showAlphaAsPercent)
      .setOriginalColor(currentColor)
      .addSaturationBrightnessComponent()
      .addColorAdjustPanel(new MaterialGraphicalColorPipetteProvider())
      .addColorValuePanel().withFocus();
    if (!recentColors.isEmpty()) {
      builder/*.addSeparator()*/
        .addCustomComponent(new ColorPickerComponentProvider() {
          @NotNull
          @Override
          public JComponent createComponent(@NotNull ColorPickerModel colorPickerModel) {
            return new RecentColorsPalette(colorPickerModel, recentColors);
          }
        });
    }
      builder.addColorListener(colorListener,true)
      .addColorListener(new ColorListener() {
        @Override
        public void colorChanged(Color color, Object source) {
          updatePointer(ref);
        }
      }, true)
        .addColorListener(new ColorListener() {
          @Override
          public void colorChanged(Color color, Object source) {
            RecentColorsComponent.saveRecentColors(RecentColorsComponent.appendColor(color, recentColors, 20));
          }
        }, false)
      .focusWhenDisplay(true)
      .setFocusCycleRoot(true)
      .addKeyAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), cancelPopup(ref))
      .addKeyAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), applyColor(ref))
      .setPopupCloseListener(popupCloseListener);
    LightCalloutPopup popup = builder.build();
    ref.set(popup);

    if (location == null) {
      location = new RelativePoint(MouseInfo.getPointerInfo().getLocation());
    }
    popup.show(location.getScreenPoint());
    updatePointer(ref);
  }

  private static boolean isEnoughSpaceToShowPopup() {
    DialogWrapper currentDialog = DialogWrapper.findInstanceFromFocus();
    if (currentDialog != null && (currentDialog.getWindow().getWidth() < 500 || currentDialog.getWindow().getHeight() < 500)) {
      return false;
    }
    return true;
  }

  private static void updatePointer(Ref<LightCalloutPopup> ref) {
    LightCalloutPopup popup = ref.get();
    Balloon balloon = popup.getBalloon();
    if (balloon instanceof BalloonImpl) {
      RelativePoint showingPoint = ((BalloonImpl)balloon).getShowingPoint();
      Color c = popup.getPointerColor(showingPoint, ((BalloonImpl)balloon).getComponent());
      if (c != null) {
        c = ColorUtil.withAlpha(c, 1.0); //clear transparency
      }
      ((BalloonImpl)balloon).setPointerColor(c);
    }
  }

  @NotNull
  private static AbstractAction cancelPopup(Ref<LightCalloutPopup> ref) {
    return new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final LightCalloutPopup popup = ref.get();
        if (popup != null) {
          popup.cancel();
        }
      }
    };
  }

  @NotNull
  private static AbstractAction applyColor(Ref<LightCalloutPopup> ref) {
    return new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final LightCalloutPopup popup = ref.get();
        if (popup != null) {
          popup.close();
        }
      }
    };
  }

  private JComponent buildTopPanel(boolean enablePipette) throws ParseException {
    final JPanel result = new JPanel(new BorderLayout());

    final JPanel previewPanel = new JPanel(new BorderLayout());
    if (enablePipette && myPicker != null) {
      final JButton pipette = new JButton();
      pipette.setUI(new BasicButtonUI());
      pipette.setRolloverEnabled(true);
      pipette.setIcon(AllIcons.Ide.Pipette);
      pipette.setBorder(JBUI.Borders.empty());
      pipette.setRolloverIcon(AllIcons.Ide.Pipette_rollover);
      pipette.setFocusable(false);
      pipette.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          myPicker.setInitialColor(getColor());
          myPicker.show();
        }
      });
      previewPanel.add(pipette, BorderLayout.WEST);
    }

    myPreviewComponent = new ColorPreviewComponent();
    previewPanel.add(myPreviewComponent, BorderLayout.CENTER);

    result.add(previewPanel, BorderLayout.NORTH);

    final JPanel rgbPanel = new JPanel();
    rgbPanel.setLayout(new BoxLayout(rgbPanel, BoxLayout.X_AXIS));
    myR_after.setPreferredSize(new Dimension(14, -1));
    myG_after.setPreferredSize(new Dimension(14, -1));
    myB_after.setPreferredSize(new Dimension(14, -1));
    rgbPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    rgbPanel.add(myR);
    rgbPanel.add(myRed);
    rgbPanel.add(myR_after);
    rgbPanel.add(Box.createHorizontalStrut(2));
    rgbPanel.add(myG);
    rgbPanel.add(myGreen);
    rgbPanel.add(myG_after);
    rgbPanel.add(Box.createHorizontalStrut(2));
    rgbPanel.add(myB);
    rgbPanel.add(myBlue);
    rgbPanel.add(myB_after);
    rgbPanel.add(Box.createHorizontalStrut(2));
    rgbPanel.add(myFormat);

    result.add(rgbPanel, BorderLayout.WEST);

    final JPanel hexPanel = new JPanel();
    hexPanel.setLayout(new BoxLayout(hexPanel, BoxLayout.X_AXIS));
    hexPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    hexPanel.add(new JLabel("#"));
    hexPanel.add(myHex);

    result.add(hexPanel, BorderLayout.EAST);

    return result;
  }

  private static final class ColorWheelPanel extends JPanel {
    private final ColorWheel myColorWheel;
    private final SlideComponent myBrightnessComponent;
    private SlideComponent myOpacityComponent = null;

    private ColorWheelPanel(ColorListener listener, boolean enableOpacity, boolean opacityInPercent) {
      setLayout(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

      myColorWheel = new ColorWheel();
      add(myColorWheel, BorderLayout.CENTER);

      myColorWheel.addListener(listener);

      myBrightnessComponent = new SlideComponent(IdeBundle.message("colorpicker.brightness.slider.title"), true);
      myBrightnessComponent.setToolTipText(IdeBundle.message("colorpicker.brightness.slider.tooltip"));
      myBrightnessComponent.addListener(value -> {
        myColorWheel.setBrightness(1f - (value / 255f));
        myColorWheel.repaint();
      });

      add(myBrightnessComponent, BorderLayout.EAST);

      if (enableOpacity) {
        myOpacityComponent = new SlideComponent(IdeBundle.message("colorpicker.opacity.slider.title"), false);
        myOpacityComponent.setUnits(opacityInPercent ? SlideComponent.Unit.PERCENT : SlideComponent.Unit.LEVEL);
        myOpacityComponent.setToolTipText(IdeBundle.message("colorpicker.opacity.slider.tooltip"));
        myOpacityComponent.addListener(integer -> {
          myColorWheel.setOpacity(integer.intValue());
          myColorWheel.repaint();
        });

        add(myOpacityComponent, BorderLayout.SOUTH);
      }
    }

    public void setColor(Color color, Object source) {
      float[] hsb = new float[3];
      Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);

      myBrightnessComponent.setValue(255 - (int)(hsb[2] * 255));
      myBrightnessComponent.repaint();

      myColorWheel.dropImage();
      if (myOpacityComponent != null && source instanceof ColorPicker) {
        myOpacityComponent.setValue(color.getAlpha());
        myOpacityComponent.repaint();
      }

      myColorWheel.setColor(color, source);
    }
  }

  private static final class ColorWheel extends JComponent {
    private static final int BORDER_SIZE = 5;
    private float myBrightness = 1f;
    private float myHue = 1f;
    private float mySaturation = 0f;

    private Image myImage;
    private Rectangle myWheel;

    private boolean myShouldInvalidate = true;

    private Color myColor;

    private final List<ColorListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
    private int myOpacity;

    private ColorWheel() {
      setOpaque(true);
      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          myShouldInvalidate = true;
        }
      });

      addMouseMotionListener(new MouseAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
          final int x = e.getX();
          final int y = e.getY();
          int mx = myWheel.x + myWheel.width / 2;
          int my = myWheel.y + myWheel.height / 2;
          double s;
          double h;
          s = Math.sqrt((x - mx) * (x - mx) + (y - my) * (y - my)) / (myWheel.height / 2);
          h = -Math.atan2(y - my, x - mx) / (2 * Math.PI);
          if (h < 0) h += 1.0;
          if (s > 1) s = 1.0;

          setHSBValue((float)h, (float)s, myBrightness, myOpacity);
        }
      });

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          final int x = e.getX();
          final int y = e.getY();
          int mx = myWheel.x + myWheel.width / 2;
          int my = myWheel.y + myWheel.height / 2;
          double s;
          double h;
          s = Math.sqrt((x - mx) * (x - mx) + (y - my) * (y - my)) / (myWheel.height / 2);
          h = -Math.atan2(y - my, x - mx) / (2 * Math.PI);
          if (h < 0) h += 1.0;
          if (s <= 1) {
            setHSBValue((float)h, (float)s, myBrightness, myOpacity);
          }
        }
      });
    }

    private void setHSBValue(float h, float s, float b, int opacity) {
      //noinspection UseJBColor
      Color rgb = new Color(Color.HSBtoRGB(h, s, b));
      setColor(ColorUtil.toAlpha(rgb, opacity), this);
    }

    private void setColor(Color color, Object source) {
      float[] hsb = new float[3];
      Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);
      myColor = color;
      myHue = hsb[0];
      mySaturation = hsb[1];
      myBrightness = hsb[2];
      myOpacity = color.getAlpha();

      fireColorChanged(source);

      repaint();
    }

    public void addListener(ColorListener listener) {
      myListeners.add(listener);
    }

    private void fireColorChanged(Object source) {
      for (ColorListener listener : myListeners) {
        listener.colorChanged(myColor, source);
      }
    }

    public void setBrightness(float brightness) {
      if (brightness != myBrightness) {
        myImage = null;
        setHSBValue(myHue, mySaturation, brightness, myOpacity);
      }
    }


    public void setOpacity(int opacity) {
      if (opacity != myOpacity) {
        setHSBValue(myHue, mySaturation, myBrightness, opacity);
      }
    }


    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
      return JBUI.size(300, 300);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2d = (Graphics2D)g;

      final Dimension size = getSize();
      int _size = Math.min(size.width, size.height);
      _size = Math.min(_size, 600);

      if (myImage != null && myShouldInvalidate) {
        if (myImage.getWidth(null) != _size) {
          myImage = null;
        }
      }

      myShouldInvalidate = false;

      if (myImage == null) {
        myImage = createImage(new ColorWheelImageProducer(_size - BORDER_SIZE * 2, _size - BORDER_SIZE * 2, myBrightness));
        myWheel = new Rectangle(BORDER_SIZE, BORDER_SIZE, _size - BORDER_SIZE * 2, _size - BORDER_SIZE * 2);
      }

      g.setColor(UIManager.getColor("Panel.background"));
      g.fillRect(0, 0, getWidth(), getHeight());

      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ((float)myOpacity) / 255f));
      g.drawImage(myImage, myWheel.x, myWheel.y, null);

      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, 1.0f));

      int mx = myWheel.x + myWheel.width / 2;
      int my = myWheel.y + myWheel.height / 2;
      //noinspection UseJBColor
      g.setColor(Color.WHITE);
      int arcw = (int)(myWheel.width * mySaturation / 2);
      int arch = (int)(myWheel.height * mySaturation / 2);
      double th = myHue * 2 * Math.PI;
      final int x = (int)(mx + arcw * Math.cos(th));
      final int y = (int)(my - arch * Math.sin(th));
      g.fillRect(x - 2, y - 2, 4, 4);
      //noinspection UseJBColor
      g.setColor(Color.BLACK);
      g.drawRect(x - 2, y - 2, 4, 4);
    }

    public void dropImage() {
      myImage = null;
    }
  }

  private static final class ColorPreviewComponent extends JComponent {
    private Color myColor;

    private ColorPreviewComponent() {
      setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
    }

    @Override
    public Dimension getPreferredSize() {
      return JBUI.size(100, 32);
    }

    public void setColor(Color c) {
      myColor = c;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      final Insets i = getInsets();
      final Rectangle r = getBounds();

      final int width = r.width - i.left - i.right;
      final int height = r.height - i.top - i.bottom;

      //noinspection UseJBColor
      g.setColor(Color.WHITE);
      g.fillRect(i.left, i.top, width, height);

      g.setColor(myColor);
      g.fillRect(i.left, i.top, width, height);

      //noinspection UseJBColor
      g.setColor(Color.BLACK);
      g.drawRect(i.left, i.top, width - 1, height - 1);

      //noinspection UseJBColor
      g.setColor(Color.WHITE);
      g.drawRect(i.left + 1, i.top + 1, width - 3, height - 3);
    }
  }

  public class NumberDocument extends PlainDocument {

    private final boolean myHex;
    private JTextField mySrc;

    public NumberDocument(boolean hex) {
      myHex = hex;
    }

    void setSource(JTextField field) {
      mySrc = field;
    }
    @Override
    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
      str = StringUtil.trimStart(str, "#");
      final boolean rgb = isRGBMode();
      char[] source = str.toCharArray();
      if (mySrc != null) {
        final int selected = mySrc.getSelectionEnd() - mySrc.getSelectionStart();
        int newLen = mySrc.getText().length() - selected + str.length();
        if (newLen > (myHex ? 6 : 3)) {
          Toolkit.getDefaultToolkit().beep();
          return;
        }
      }
      char[] result = new char[source.length];
      int j = 0;
      for (int i = 0; i < result.length; i++) {
        if (myHex ? "0123456789abcdefABCDEF".indexOf(source[i]) >= 0 : Character.isDigit(source[i])) {
          result[j++] = source[i];
        }
        else {
          Toolkit.getDefaultToolkit().beep();
        }
      }
      final String toInsert = StringUtil.toUpperCase(new String(result, 0, j));
      final String res = new StringBuilder(mySrc.getText()).insert(offs, toInsert).toString();
      try {
        if (!myHex) {
          final int num = Integer.parseInt(res);
          if (rgb) {
            if (num > 255) {
              Toolkit.getDefaultToolkit().beep();
              return;
            }
          }
          else {
            if ((mySrc == myRed && num > 359)
                || ((mySrc == myGreen || mySrc == myBlue) && num > 100)) {
              Toolkit.getDefaultToolkit().beep();
              return;
            }
          }
        }
      }
      catch (NumberFormatException ignore) {
      }
      super.insertString(offs, toInsert, a);
    }
  }

  private static final class RecentColorsComponent extends JComponent {
    private static final int WIDTH = 10 * 30 + 13;
    private static final int HEIGHT = 62 + 3;

    private List<Color> myRecentColors = new ArrayList<>();

    private RecentColorsComponent(final ColorListener listener, boolean restoreColors) {
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          Color color = getColor(e);
          if (color != null) {
            listener.colorChanged(color, RecentColorsComponent.this);
          }
        }
      });

      if (restoreColors) {
        myRecentColors.clear();
        myRecentColors.addAll(getRecentColors());
      }
    }

    @Nullable
    public Color getMostRecentColor() {
      return myRecentColors.isEmpty() ? null : myRecentColors.get(myRecentColors.size() - 1);
    }

    @SuppressWarnings("UseJBColor")
    public static List<Color> getRecentColors() {
      final String value = PropertiesComponent.getInstance().getValue(COLOR_CHOOSER_COLORS_KEY);
      if (value != null) {
        final List<String> colors = StringUtil.split(value, ",,,");
        ArrayList<Color> recentColors = new ArrayList<>();
        for (String color : colors) {
          if (color.contains("-")) {
            List<String> components = StringUtil.split(color, "-");
            if (components.size() == 4) {
              recentColors.add(new Color(Integer.parseInt(components.get(0)),
                                           Integer.parseInt(components.get(1)),
                                           Integer.parseInt(components.get(2)),
                                           Integer.parseInt(components.get(3))));
            }
          }
          else {
            recentColors.add(new Color(Integer.parseInt(color)));
          }
        }
        return recentColors;
      }
      return Collections.emptyList();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      Color color = getColor(event);
      if (color != null) {
        return IdeBundle.message("colorpicker.recent.color.tooltip",
                                 color.getRed(), color.getGreen(), color.getBlue(),
                                 String.format("%.2f", (float)(color.getAlpha() / 255.0)));
      }

      return super.getToolTipText(event);
    }

    @Nullable
    private Color getColor(MouseEvent event) {
      Couple<Integer> pair = pointToCellCoords(event.getPoint());
      if (pair != null) {
        int ndx = pair.second + pair.first * 10;
        if (myRecentColors.size() > ndx) {
          return myRecentColors.get(ndx);
        }
      }

      return null;
    }

    public void saveColors() {
      saveRecentColors(myRecentColors);
    }

    private static void saveRecentColors(List<? extends Color> recentColors) {
      final List<String> values = new ArrayList<>();
      for (Color recentColor : recentColors) {
        if (recentColor == null) break;
        values
          .add(String.format("%d-%d-%d-%d", recentColor.getRed(), recentColor.getGreen(), recentColor.getBlue(), recentColor.getAlpha()));
      }

      PropertiesComponent.getInstance().setValue(COLOR_CHOOSER_COLORS_KEY, values.isEmpty() ? null : StringUtil.join(values, ",,,"), null);
    }

    private static List<Color> appendColor(Color color, List<? extends Color> recentColors, int maxSize) {
      ArrayList<Color> colors = new ArrayList<>(recentColors);
      colors.remove(color);
      colors.add(0, color);

      if (colors.size() > maxSize) {
        colors = new ArrayList<>(recentColors.subList(0, maxSize));
      }
      return colors;
    }

    public void appendColor(Color c) {
      if (!myRecentColors.contains(c)) {
        myRecentColors.add(c);
      }

      if (myRecentColors.size() > 20) {
        myRecentColors = new ArrayList<>(myRecentColors.subList(myRecentColors.size() - 20, myRecentColors.size()));
      }
    }

    @Nullable
    private Couple<Integer> pointToCellCoords(Point p) {
      int x = p.x;
      int y = p.y;

      final Insets i = getInsets();
      final Dimension d = getSize();

      final int left = i.left + (d.width - i.left - i.right - WIDTH) / 2;
      final int top = i.top + (d.height - i.top - i.bottom - HEIGHT) / 2;

      int col = (x - left - 2) / 31;
      col = Math.min(col, 9);
      int row = (y - top - 2) / 31;
      row = Math.min(row, 1);

      return row >= 0 && col >= 0 ? Couple.of(row, col) : null;
    }

    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(WIDTH, HEIGHT);
    }

    @SuppressWarnings("UseJBColor")
    @Override
    protected void paintComponent(Graphics g) {
      final Insets i = getInsets();

      final Dimension d = getSize();

      final int left = i.left + (d.width - i.left - i.right - WIDTH) / 2;
      final int top = i.top + (d.height - i.top - i.bottom - HEIGHT) / 2;

      g.setColor(Color.WHITE);
      g.fillRect(left, top, WIDTH, HEIGHT);

      g.setColor(Color.GRAY);
      g.drawLine(left + 1, i.top + HEIGHT / 2, left + WIDTH - 3, i.top + HEIGHT / 2);
      g.drawRect(left + 1, top + 1, WIDTH - 3, HEIGHT - 3);


      for (int k = 1; k < 10; k++) {
        g.drawLine(left + 1 + k * 31, top + 1, left + 1 + k * 31, top + HEIGHT - 3);
      }

      for (int r = 0; r < myRecentColors.size(); r++) {
        int row = r / 10;
        int col = r % 10;
        Color color = myRecentColors.get(r);
        g.setColor(color);
        g.fillRect(left + 2 + col * 30 + col + 1, top + 2 + row * 30 + row + 1, 28, 28);
      }
    }
  }

  static class ColorPickerDialog extends DialogWrapper {
    private final Color myPreselectedColor;
    private final List<? extends ColorPickerListener> myListeners;
    private ColorPicker myColorPicker;
    private final boolean myEnableOpacity;
    private final boolean myOpacityInPercent;

    ColorPickerDialog(@NotNull Component parent,
                      @DialogTitle String caption,
                      @Nullable Color preselectedColor,
                      boolean enableOpacity,
                      List<? extends ColorPickerListener> listeners,
                      boolean opacityInPercent) {
      super(parent, true);
      myListeners = listeners;
      myPreselectedColor = preselectedColor;
      myEnableOpacity = enableOpacity;
      myOpacityInPercent = opacityInPercent;

      setTitle(caption);
      setResizable(false);
      setOKButtonText(listeners == null || listeners.size() > 0 ? IdeBundle.message("button.choose") : IdeBundle.message("button.copy"));
      super.init();
    }

    @Override
    protected JComponent createCenterPanel() {
      if (myColorPicker == null) {
        myColorPicker = new ColorPicker(myDisposable, myPreselectedColor, true, myEnableOpacity, myListeners, myOpacityInPercent);
      }

      return myColorPicker;
    }

    public Color getColor() {
      return myColorPicker.getColor();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myColorPicker.getPreferredFocusedComponent();
    }

    @Override
    protected void doOKAction() {
      myColorPicker.appendRecentColor();
      myColorPicker.saveRecentColors();

      if (myListeners != null && myListeners.isEmpty()) {
        String color = StringUtil.toUpperCase(ColorUtil.toHex(myColorPicker.myColor));
        CopyPasteManager.getInstance().setContents(new StringSelection(color));
      }
      super.doOKAction();
    }

    @Override
    public void show() {
      super.show();
      myColorPicker.fireClosed(getExitCode() == DialogWrapper.OK_EXIT_CODE ? getColor() : null);
    }
  }

  public static class ColorWheelImageProducer extends MemoryImageSource {
    private final int[] myPixels;
    private final int myWidth;
    private final int myHeight;
    private float myBrightness = 1f;

    private float[] myHues;
    private float[] mySat;
    private int[] myAlphas;

    public ColorWheelImageProducer(int w, int h, float brightness) {
      super(w, h, null, 0, w);
      myPixels = new int[w * h];
      myWidth = w;
      myHeight = h;
      myBrightness = brightness;
      generateLookupTables();
      newPixels(myPixels, ColorModel.getRGBdefault(), 0, w);
      setAnimated(true);
      generateColorWheel();
    }

    public int getRadius() {
      return Math.min(myWidth, myHeight) / 2 - 2;
    }

    private void generateLookupTables() {
      mySat = new float[myWidth * myHeight];
      myHues = new float[myWidth * myHeight];
      myAlphas = new int[myWidth * myHeight];
      float radius = getRadius();

      // blend is used to create a linear alpha gradient of two extra pixels
      float blend = (radius + 2f) / radius - 1f;

      // Center of the color wheel circle
      int cx = myWidth / 2;
      int cy = myHeight / 2;

      for (int x = 0; x < myWidth; x++) {
        int kx = x - cx; // Kartesian coordinates of x
        int squarekx = kx * kx; // Square of kartesian x

        for (int y = 0; y < myHeight; y++) {
          int ky = cy - y; // Kartesian coordinates of y

          int index = x + y * myWidth;
          mySat[index] = (float)Math.sqrt(squarekx + ky
                                                     * ky)
                         / radius;
          if (mySat[index] <= 1f) {
            myAlphas[index] = 0xff000000;
          }
          else {
            myAlphas[index] = (int)((blend - Math.min(blend,
                                                      mySat[index] - 1f)) * 255 / blend) << 24;
            mySat[index] = 1f;
          }
          if (myAlphas[index] != 0) {
            myHues[index] = (float)(Math.atan2(ky, kx) / Math.PI / 2d);
          }
        }
      }
    }

    public void generateColorWheel() {
      for (int index = 0; index < myPixels.length; index++) {
        if (myAlphas[index] != 0) {
          myPixels[index] = myAlphas[index]
                            | 0xffffff
                              & Color.HSBtoRGB(myHues[index],
                                               mySat[index], myBrightness);
        }
      }
      newPixels();
    }
  }

  private static final class DefaultColorPipette extends ColorPipetteBase {
    private static final int SIZE = 30;
    private static final int DIALOG_SIZE = SIZE - 4;
    private static final Point HOT_SPOT = new Point(DIALOG_SIZE / 2, DIALOG_SIZE / 2);

    private final Rectangle myCaptureRect = new Rectangle(-4, -4, 8, 8);
    private final Rectangle myZoomRect = new Rectangle(0, 0, SIZE, SIZE);
    private final Point myPreviousLocation = new Point();

    private Graphics2D myGraphics;
    private BufferedImage myImage;
    private BufferedImage myPipetteImage;
    private BufferedImage myMaskImage;
    private final Timer myTimer;

    private DefaultColorPipette(@NotNull JComponent parent, @NotNull ColorListener colorListener) {
      super(parent, colorListener);
      myTimer = TimerUtil.createNamedTimer("DefaultColorPipette", 5, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          updatePipette();
        }
      });
    }

    @Override
    protected Color getPixelColor(Point location) {
      return super.getPixelColor(new Point(location.x - HOT_SPOT.x + SIZE / 2, location.y - HOT_SPOT.y + SIZE / 2));
    }

    @Override
    public Dialog show() {
      Dialog picker = super.show();
      myTimer.start();
      // it seems like it's the lowest value for opacity for mouse events to be processed correctly
      WindowManager.getInstance().setAlphaModeRatio(picker, SystemInfo.isMac ? 0.95f : 0.99f);

      Area area = new Area(new Rectangle(0, 0, DIALOG_SIZE, DIALOG_SIZE));
      area.subtract(new Area(new Rectangle(SIZE / 2 - 1, SIZE / 2 - 1, 3, 3)));
      picker.setShape(area);
      return picker;
    }

    @Override
    public boolean isAvailable() {
      if (myRobot != null) {
        myRobot.createScreenCapture(new Rectangle(0, 0, 1, 1));
        return WindowManager.getInstance().isAlphaModeSupported();
      }
      return false;
    }

    @Override
    @NotNull
    @SuppressWarnings("UseJBColor")
    protected Dialog getOrCreatePickerDialog() {
      Dialog pickerDialog = getPickerDialog();
      if (pickerDialog == null) {
        pickerDialog = super.getOrCreatePickerDialog();
        pickerDialog.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseExited(MouseEvent event) {
            updatePipette();
          }
        });
        pickerDialog.addMouseMotionListener(new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            updatePipette();
          }
        });
        pickerDialog.addFocusListener(new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent e) {
            if (e.isTemporary()) {
              pickAndClose();
            } else {
              cancelPipette();
            }
          }
        });

        pickerDialog.setSize(DIALOG_SIZE, DIALOG_SIZE);
        myMaskImage = UIUtil.createImage(pickerDialog, SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D maskG = myMaskImage.createGraphics();
        maskG.setColor(Color.BLUE);
        maskG.fillRect(0, 0, SIZE, SIZE);

        maskG.setColor(Color.RED);
        maskG.setComposite(AlphaComposite.SrcOut);
        maskG.fillRect(0, 0, SIZE, SIZE);
        maskG.dispose();

        myPipetteImage = UIUtil.createImage(pickerDialog, AllIcons.Ide.Pipette.getIconWidth(), AllIcons.Ide.Pipette.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = myPipetteImage.createGraphics();
        AllIcons.Ide.Pipette.paintIcon(null, graphics, 0, 0);
        graphics.dispose();

        myImage = myParent.getGraphicsConfiguration().createCompatibleImage(SIZE, SIZE, Transparency.TRANSLUCENT);

        myGraphics = (Graphics2D)myImage.getGraphics();
        myGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      }

      return pickerDialog;
    }

    private void updatePipette() {
      Dialog pickerDialog = getPickerDialog();
      if (pickerDialog != null && pickerDialog.isShowing()) {
        Point mouseLoc = updateLocation();
        if (mouseLoc == null) return;
        final Color c = getPixelColor(mouseLoc);
        if (!c.equals(getColor()) || !mouseLoc.equals(myPreviousLocation)) {
          setColor(c);
          myPreviousLocation.setLocation(mouseLoc);
          myCaptureRect.setBounds(mouseLoc.x - HOT_SPOT.x + SIZE / 2 - 2, mouseLoc.y - HOT_SPOT.y + SIZE / 2 - 2, 5, 5);

          BufferedImage capture = myRobot.createScreenCapture(myCaptureRect);

          // Clear the cursor graphics
          myGraphics.setComposite(AlphaComposite.Src);
          myGraphics.setColor(UIUtil.TRANSPARENT_COLOR);
          myGraphics.fillRect(0, 0, myImage.getWidth(), myImage.getHeight());

          myGraphics.drawImage(capture, myZoomRect.x, myZoomRect.y, myZoomRect.width, myZoomRect.height, this);

          // cropping round image
          myGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT));
          myGraphics.drawImage(myMaskImage, myZoomRect.x, myZoomRect.y, myZoomRect.width, myZoomRect.height, this);

          // paint magnifier
          myGraphics.setComposite(AlphaComposite.SrcOver);

          UIUtil.drawImage(myGraphics, myPipetteImage, SIZE - AllIcons.Ide.Pipette.getIconWidth(), 0, this);

          pickerDialog.setCursor(myParent.getToolkit().createCustomCursor(myImage, HOT_SPOT, "ColorPicker"));
          notifyListener(c, 300);
        }
      }
    }

    @Override
    public void cancelPipette() {
      myTimer.stop();
      super.cancelPipette();
    }

    @Override
    public void dispose() {
      myTimer.stop();
      super.dispose();
      if (myGraphics != null) {
        myGraphics.dispose();
      }
      myImage = null;
      myPipetteImage = null;
      myMaskImage = null;
    }
  }
}

