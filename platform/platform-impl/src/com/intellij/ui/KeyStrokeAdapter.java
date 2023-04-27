// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class KeyStrokeAdapter implements KeyListener {
  private static final Logger LOG = Logger.getInstance(KeyStrokeAdapter.class);

  @Override
  public void keyTyped(KeyEvent event) {
    keyTyped(event, getDefaultKeyStroke(event));
  }

  protected boolean keyTyped(KeyStroke stroke) {
    return false;
  }

  private void keyTyped(KeyEvent event, KeyStroke stroke) {
    if (stroke != null && keyTyped(stroke)) {
      event.consume();
    }
  }

  @Override
  public void keyPressed(KeyEvent event) {
    keyPressed(event, getDefaultKeyStroke(event));
  }

  protected boolean keyPressed(KeyStroke stroke) {
    return false;
  }

  private void keyPressed(KeyEvent event, KeyStroke stroke) {
    if (stroke != null && keyPressed(stroke)) {
      event.consume();
    }
  }

  @Override
  public void keyReleased(KeyEvent event) {
    keyReleased(event, getDefaultKeyStroke(event));
  }

  protected boolean keyReleased(KeyStroke stroke) {
    return false;
  }

  private void keyReleased(KeyEvent event, KeyStroke stroke) {
    if (stroke != null && keyReleased(stroke)) {
      event.consume();
    }
  }

  /**
   * @param event the specified key event to process
   * @return a key stroke or {@code null} if it is not applicable
   * @see JComponent#processKeyBindings(KeyEvent, boolean)
   */
  public static KeyStroke getDefaultKeyStroke(KeyEvent event) {
    if (event != null && !event.isConsumed()) {
      int id = event.getID();
      if (id == KeyEvent.KEY_TYPED) {
        return getKeyStroke(event.getKeyChar(), 0);
      }
      boolean released = id == KeyEvent.KEY_RELEASED;
      if (released || id == KeyEvent.KEY_PRESSED) {
        int code = event.getKeyCode();
        if (code == KeyEvent.VK_UNDEFINED) {
          code = event.getExtendedKeyCode();
        }
        return getKeyStroke(code, event.getModifiers(), released);
      }
    }
    return null;
  }

  /**
   * @param ch        the specified key character
   * @param modifiers the modifier mask from the event
   * @return a key stroke or {@code null} if {@code ch} is undefined
   */
  private static KeyStroke getKeyStroke(char ch, int modifiers) {
    return KeyEvent.CHAR_UNDEFINED == ch ? null : KeyStroke.getKeyStroke(Character.valueOf(ch), modifiers);
  }

  /**
   * @param code      the numeric code for a keyboard key
   * @param modifiers the modifier mask from the event
   * @param released  {@code true} if the key stroke should represent a key release
   * @return a key stroke or {@code null} if {@code code} is undefined
   */
  private static KeyStroke getKeyStroke(int code, int modifiers, boolean released) {
    return KeyEvent.VK_UNDEFINED == code ? null : KeyStroke.getKeyStroke(code, modifiers, released);
  }

  /**
   * Parses a string and returns the corresponding key stroke.
   * The string must have the following syntax:
   * <pre>
   *    &lt;modifiers&gt;* (&lt;typedID&gt; | &lt;pressedReleasedID&gt;)
   * </pre>where<pre>
   *    modifiers := shift | ctrl | control | meta | alt | altGr | altGraph
   *    typedID := typed &lt;char&gt;
   *    pressedReleasedID := (pressed | released) key
   * </pre>
   * If {@code typed}, {@code pressed} or {@code released} is not specified, {@code pressed} is assumed.
   * The {@code char} is a string of length 1 giving Unicode character.
   * The {@code key} is a virtual key name or an integer that represents a key code.
   * Note that the virtual key name is a second part of a name
   * of the corresponding field defined in the {@link KeyEvent} class.
   * <p/>
   * This method has two differences from the {@link KeyStroke#getKeyStroke(String)} method.
   * First, it does not throw an exception if the specified string cannot be parsed.
   * Second, it supports an integer representation of a key code
   * if the corresponding virtual key is not specified in the {@link KeyEvent} class.
   * <p/>
   * This method returns {@code null}
   * if the specified string is {@code null} or if it cannot be parsed.
   * The error message is logged without throwing an exception.
   *
   * @param string the specified string to parse as described above
   * @return a key stroke string represented by the specified string
   */
  public static KeyStroke getKeyStroke(String string) {
    if (string != null) {
      StringTokenizer st = new StringTokenizer(string, " ");

      int modifiers = 0;
      boolean typed = false;
      boolean pressed = false;
      boolean released = false;

      int count = st.countTokens();
      for (int i = 1; i <= count; i++) {
        String token = st.nextToken();
        if (typed) {
          if (st.hasMoreTokens()) {
            LOG.error("key stroke declaration has more tokens: " + st.nextToken());
            return null;
          }
          if (token.length() != 1) {
            LOG.error("unexpected key stroke character: " + token);
            return null;
          }
          return getKeyStroke(token.charAt(0), modifiers);
        }
        String tokenLowerCase = StringUtil.toLowerCase(token);
        if (pressed || released || i == count) {
          if (st.hasMoreTokens()) {
            LOG.error("key stroke declaration has more tokens: " + st.nextToken());
            return null;
          }
          Integer code = LazyVirtualKeys.myNameToCode.get(tokenLowerCase);
          if (code == null) {
            try {
              code = Integer.decode(token);
            }
            catch (NumberFormatException exception) {
              LOG.error("unexpected key stroke code: " + token);
              return null;
            }
          }
          return getKeyStroke(code, modifiers, released);
        }
        switch (tokenLowerCase) {
          case "typed" -> typed = true;
          case "pressed" -> pressed = true;
          case "released" -> released = true;
          default -> {
            Integer mask = LazyModifiers.mapNameToMask.get(tokenLowerCase);
            if (mask == null) {
              LOG.error("unexpected key stroke modifier: " + token);
              return null;
            }
            modifiers |= mask;
          }
        }
      }
      LOG.error("key stroke declaration is not completed");
    }
    return null;
  }

  /**
   * Returns a string that represents the specified key stroke.
   * The result of this method can be passed as a parameter
   * to the {@link #getKeyStroke(String)} method to produce
   * a key stroke equal to the specified key stroke.
   * <p/>
   * This method has two differences from the {@link KeyStroke#toString()} method.
   * First, the resulting string does not contain the "pressed" keyword.
   * Second, the resulting string contains a hexadecimal integer instead of {@code null}
   * if the corresponding virtual key is not specified in the {@link KeyEvent} class.
   * <p/>
   * This method returns {@code null}
   * if the specified key stroke is {@code null} or
   * if its {@code keyCode} and {@code keyChar} both are undefined.
   *
   * @param stroke the specified key stroke to process
   * @return a string representation of the specified key stroke
   */
  public static String toString(KeyStroke stroke) {
    if (stroke != null) {
      StringBuilder sb = new StringBuilder();

      int modifiers = stroke.getModifiers();
      append(sb, "shift", modifiers, InputEvent.SHIFT_DOWN_MASK);
      append(sb, "ctrl", modifiers, InputEvent.CTRL_DOWN_MASK);
      append(sb, "meta", modifiers, InputEvent.META_DOWN_MASK);
      append(sb, "alt", modifiers, InputEvent.ALT_DOWN_MASK);
      append(sb, "altGraph", modifiers, InputEvent.ALT_GRAPH_DOWN_MASK);
      append(sb, "button1", modifiers, InputEvent.BUTTON1_DOWN_MASK);
      append(sb, "button2", modifiers, InputEvent.BUTTON2_DOWN_MASK);
      append(sb, "button3", modifiers, InputEvent.BUTTON3_DOWN_MASK);

      int code = stroke.getKeyCode();
      if (code != KeyEvent.VK_UNDEFINED) {
        append(sb, "released", stroke.isOnKeyRelease());
        String name = LazyVirtualKeys.myCodeToName.get(code);
        if (name == null) {
          sb.append('#');
          name = Integer.toHexString(code);
        }
        return sb.append(name).toString();
      }
      char ch = stroke.getKeyChar();
      if (ch != KeyEvent.CHAR_UNDEFINED) {
        append(sb, "typed", true);
        return sb.append(ch).toString();
      }
      LOG.error("undefined key stroke");
    }
    return null;
  }

  private static void append(StringBuilder sb, String name, int modifiers, int mask) {
    append(sb, name, (modifiers & mask) != 0);
  }

  private static void append(StringBuilder sb, String name, boolean set) {
    if (set) sb.append(name).append(' ');
  }

  private static final class LazyModifiers {
    private static final Map<String, Integer> mapNameToMask = new HashMap<>();

    static {
      mapNameToMask.put("shift", InputEvent.SHIFT_DOWN_MASK | InputEvent.SHIFT_MASK);
      mapNameToMask.put("ctrl", InputEvent.CTRL_DOWN_MASK | InputEvent.CTRL_MASK); // duplicate
      mapNameToMask.put("control", InputEvent.CTRL_DOWN_MASK | InputEvent.CTRL_MASK);
      mapNameToMask.put("meta", InputEvent.META_DOWN_MASK | InputEvent.META_MASK);
      mapNameToMask.put("alt", InputEvent.ALT_DOWN_MASK | InputEvent.ALT_MASK);
      mapNameToMask.put("altgr", InputEvent.ALT_GRAPH_DOWN_MASK | InputEvent.ALT_GRAPH_MASK); // duplicate
      mapNameToMask.put("altgraph", InputEvent.ALT_GRAPH_DOWN_MASK | InputEvent.ALT_GRAPH_MASK);
      mapNameToMask.put("button1", InputEvent.BUTTON1_DOWN_MASK);
      mapNameToMask.put("button2", InputEvent.BUTTON2_DOWN_MASK);
      mapNameToMask.put("button3", InputEvent.BUTTON3_DOWN_MASK);
    }
  }

  private static final class LazyVirtualKeys {
    private static final Map<String, Integer> myNameToCode = new HashMap<>();
    private static final Int2ObjectMap<String> myCodeToName = new Int2ObjectOpenHashMap<>();

    static {
      try {
        for (Field field : KeyEvent.class.getFields()) {
          String name = field.getName();
          if (name.startsWith("VK_")) {
            name = StringUtil.toLowerCase(name.substring(3));
            int code = field.getInt(KeyEvent.class);
            myNameToCode.put(name, code);
            myCodeToName.put(code, name);
          }
        }
      }
      catch (Exception exception) {
        LOG.error(exception);
      }
    }
  }
}
