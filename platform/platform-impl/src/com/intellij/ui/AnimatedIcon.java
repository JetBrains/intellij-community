/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Graphics;

/**
 * @author Sergey.Malenkov
 */
public class AnimatedIcon implements Icon {
  public interface Frame {
    @NotNull
    Icon getIcon();

    int getDelay();
  }

  public static final class Default extends AnimatedIcon {
    public Default() {
      super(100,
            AllIcons.Process.Step_1,
            AllIcons.Process.Step_2,
            AllIcons.Process.Step_3,
            AllIcons.Process.Step_4,
            AllIcons.Process.Step_5,
            AllIcons.Process.Step_6,
            AllIcons.Process.Step_7,
            AllIcons.Process.Step_8,
            AllIcons.Process.Step_9,
            AllIcons.Process.Step_10,
            AllIcons.Process.Step_11,
            AllIcons.Process.Step_12);
    }
  }

  public static final class Big extends AnimatedIcon {
    public Big() {
      super(100,
            AllIcons.Process.Big.Step_1,
            AllIcons.Process.Big.Step_2,
            AllIcons.Process.Big.Step_3,
            AllIcons.Process.Big.Step_4,
            AllIcons.Process.Big.Step_5,
            AllIcons.Process.Big.Step_6,
            AllIcons.Process.Big.Step_7,
            AllIcons.Process.Big.Step_8,
            AllIcons.Process.Big.Step_9,
            AllIcons.Process.Big.Step_10,
            AllIcons.Process.Big.Step_11,
            AllIcons.Process.Big.Step_12);
    }
  }

  public static final class Grey extends AnimatedIcon {
    public Grey() {
      super(150,
            AllIcons.Process.State.GreyProgr_1,
            AllIcons.Process.State.GreyProgr_2,
            AllIcons.Process.State.GreyProgr_3,
            AllIcons.Process.State.GreyProgr_4,
            AllIcons.Process.State.GreyProgr_5,
            AllIcons.Process.State.GreyProgr_6,
            AllIcons.Process.State.GreyProgr_7,
            AllIcons.Process.State.GreyProgr_8);
    }
  }

  public static final class FS extends AnimatedIcon {
    public FS() {
      super(50,
            AllIcons.Process.FS.Step_1,
            AllIcons.Process.FS.Step_2,
            AllIcons.Process.FS.Step_3,
            AllIcons.Process.FS.Step_4,
            AllIcons.Process.FS.Step_5,
            AllIcons.Process.FS.Step_6,
            AllIcons.Process.FS.Step_7,
            AllIcons.Process.FS.Step_8,
            AllIcons.Process.FS.Step_9,
            AllIcons.Process.FS.Step_10,
            AllIcons.Process.FS.Step_11,
            AllIcons.Process.FS.Step_12,
            AllIcons.Process.FS.Step_13,
            AllIcons.Process.FS.Step_14,
            AllIcons.Process.FS.Step_15,
            AllIcons.Process.FS.Step_16,
            AllIcons.Process.FS.Step_17,
            AllIcons.Process.FS.Step_18);
    }
  }


  private final Frame[] frames;
  private boolean requested;
  private long time;
  private int index;
  private Frame frame;

  public AnimatedIcon(int delay, @NotNull Icon... icons) {
    this(getFrames(delay, icons));
  }

  public AnimatedIcon(@NotNull Frame... frames) {
    this.frames = frames;
    assert frames.length > 0 : "empty array";
    for (Frame frame : frames) assert frame != null : "null animation frame";
    updateFrameAt(System.currentTimeMillis());
  }

  private static Frame[] getFrames(int delay, @NotNull Icon... icons) {
    int length = icons.length;
    assert length > 0 : "empty array";
    Frame[] frames = new Frame[length];
    for (int i = 0; i < length; i++) {
      Icon icon = icons[i];
      assert icon != null : "null icon";
      frames[i] = new Frame() {
        @NotNull
        @Override
        public Icon getIcon() {
          return icon;
        }

        @Override
        public int getDelay() {
          return delay;
        }
      };
    }
    return frames;
  }

  private void updateFrameAt(long current) {
    if (frames.length <= index) index = 0;
    frame = frames[index++];
    time = current;
  }

  private Icon getUpdatedIcon() {
    long current = System.currentTimeMillis();
    if (frame.getDelay() <= (current - time)) updateFrameAt(current);
    return frame.getIcon();
  }

  @Override
  public final void paintIcon(Component c, Graphics g, int x, int y) {
    Icon icon = getUpdatedIcon();
    if (!requested && canRefresh(c)) {
      int delay = frame.getDelay();
      if (delay > 0) {
        requested = true;
        Timer timer = new Timer(delay, event -> {
          requested = false;
          if (canRefresh(c)) {
            doRefresh(c);
          }
        });
        timer.setRepeats(false);
        timer.start();
      }
      else {
        doRefresh(c);
      }
    }
    icon.paintIcon(c, g, x, y);
  }

  @Override
  public final int getIconWidth() {
    return getUpdatedIcon().getIconWidth();
  }

  @Override
  public final int getIconHeight() {
    return getUpdatedIcon().getIconHeight();
  }

  protected boolean canRefresh(Component component) {
    return component != null && component.isShowing();
  }

  protected void doRefresh(Component component) {
    if (component != null) component.repaint();
  }
}
