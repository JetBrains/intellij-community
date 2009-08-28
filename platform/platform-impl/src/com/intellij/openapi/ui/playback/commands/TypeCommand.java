package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.commands.KeyStokeMap;
import com.intellij.openapi.util.registry.Registry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public abstract class TypeCommand extends AbstractCommand {

  private KeyStokeMap myMap = new KeyStokeMap();

  public TypeCommand(String text, int line) {
    super(text, line);
  }

  protected void type(Robot robot, int code, int modfiers) {
    type(robot, KeyStroke.getKeyStroke(code, modfiers));
  }

  protected void type(Robot robot, KeyStroke keyStroke) {
    boolean shift = (keyStroke.getModifiers() & KeyEvent.SHIFT_MASK) > 0;
    boolean alt = (keyStroke.getModifiers() & KeyEvent.ALT_MASK) > 0;
    boolean control = (keyStroke.getModifiers() & KeyEvent.ALT_MASK) > 0;
    boolean meta = (keyStroke.getModifiers() & KeyEvent.META_MASK) > 0;

    if (shift) {
      robot.keyPress(KeyEvent.VK_SHIFT);
    }

    if (control) {
      robot.keyPress(KeyEvent.VK_CONTROL);
    }

    if (alt) {
      robot.keyPress(KeyEvent.VK_ALT);
    }

    if (meta) {
      robot.keyPress(KeyEvent.VK_META);
    }

    robot.keyPress(keyStroke.getKeyCode());
    robot.delay(Registry.intValue("actionSystem.playback.autodelay"));
    robot.keyRelease(keyStroke.getKeyCode());

    if (shift) {
      robot.keyRelease(KeyEvent.VK_SHIFT);
    }

    if (control) {
      robot.keyRelease(KeyEvent.VK_CONTROL);
    }

    if (alt) {
      robot.keyRelease(KeyEvent.VK_ALT);
    }

    if (meta) {
      robot.keyRelease(KeyEvent.VK_META);
    }
  }

  protected KeyStroke get(char c) {
    return myMap.get(c);
  }

  protected KeyStroke getFromShortcut(String sc) {
    return myMap.get(sc);
  }
}