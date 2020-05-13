// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

final class Converter01 {
  @NonNls
  private static final String KEY_MAP = "keymap";
  @NonNls
  private static final String UNKNOWN_ELEMENT = "unknown element: ";
  @NonNls
  private static final String UNKNOWN_VERSION = "unknown version: ";
  @NonNls
  private static final String VERSION = "version";
  @NonNls
  private static final String DISABLE_MNEMONICS = "disableMnemonics";
  @NonNls
  private static final String DISABLE_MNEMONICS_ATTRIBUTE = "disable-Mnemonics";
  @NonNls
  private static final String NAME_ATTRIBUTE = "name";
  @NonNls
  private static final String BINDING = "binding";
  @NonNls
  private static final String ID_ATTRIBUTE = "id";
  @NonNls
  private static final String KEYSTROKE_ATTRIBUTE = "keystroke";
  @NonNls
  private static final String SUFFIX_ATTRIBUTE = "suffix";
  @NonNls
  private static final String KEYBOARD_SHORTCUT = "keyboard-shortcut";
  @NonNls
  private static final String FIRST_KEYSTROKE_ATTRIBUTE = "first-keystroke";
  @NonNls
  private static final String SECOND_KEYSTROKE_ATTRIBUTE = "second-keystroke";
  @NonNls
  private static final String MOUSE_SHORTCUT = "mouse-shortcut";
  @NonNls
  private static final String ACTION = "action";

  /**
   * Converts keymap from version "0" (no version specified)
   * to version "1".
   *
   * @param keymapElement XML element that corresponds to "keymap" tag.
   */
  public static void convert(Element keymapElement) throws InvalidDataException {
    if (!KEY_MAP.equals(keymapElement.getName())) {
      throw new IllegalArgumentException(UNKNOWN_ELEMENT + keymapElement);
    }
    String version = keymapElement.getAttributeValue(VERSION);
    if (version != null) {
      throw new InvalidDataException(UNKNOWN_VERSION + version);
    }

    // Add version

    keymapElement.setAttribute(VERSION, Integer.toString(1));

    // disableMnemonics -> disable-mnemonics

    boolean disableMnemonics = Boolean.valueOf(DISABLE_MNEMONICS).booleanValue();
    keymapElement.removeAttribute(DISABLE_MNEMONICS);
    keymapElement.setAttribute(DISABLE_MNEMONICS_ATTRIBUTE, Boolean.toString(disableMnemonics));

    // Now we have to separate all shortcuts by action's ID and convert binding to keyboard-shortcut

    String name = keymapElement.getAttributeValue(NAME_ATTRIBUTE);
    if (name == null) {
      throw new InvalidDataException("Attribute 'name' of <keymap> must be specified");
    }
    HashMap<String, ArrayList<Element>> id2elements = new HashMap<>();

    for (Iterator<Element> i = keymapElement.getChildren().iterator(); i.hasNext(); ) {
      Element oldChild = i.next();
      if (BINDING.equals(oldChild.getName())) { // binding -> keyboard-shortcut
        // Remove attribute "id"
        String id = oldChild.getAttributeValue(ID_ATTRIBUTE);
        if (id == null) {
          throw new InvalidDataException("attribute 'id' must be specified");
        }
        // keystroke -> first-keystroke
        String keystroke = oldChild.getAttributeValue(KEYSTROKE_ATTRIBUTE);
        // suffix -> second-keystroke
        String suffix = oldChild.getAttributeValue(SUFFIX_ATTRIBUTE);
        if (keystroke != null) {
          Element newChild = new Element(KEYBOARD_SHORTCUT);
          newChild.setAttribute(FIRST_KEYSTROKE_ATTRIBUTE, keystroke);
          if (suffix != null) {
            newChild.setAttribute(SECOND_KEYSTROKE_ATTRIBUTE, suffix);
          }
          // Put new child into the map
          ArrayList<Element> elements = id2elements.get(id);
          if (elements == null) {
            elements = new ArrayList<>(2);
            id2elements.put(id, elements);
          }
          elements.add(newChild);
        }
        else {
          id2elements.put(id, new ArrayList<>(0));
        }
        // Remove old child
        i.remove();
      }
      else if (MOUSE_SHORTCUT.equals(oldChild.getName())) {
        // Remove attribute "id"
        String id = oldChild.getAttributeValue(ID_ATTRIBUTE);
        if (id == null) {
          throw new InvalidDataException("Attribute 'id' of <mouse-shortcut> must be specified; keymap name=" + name);
        }
        oldChild.removeAttribute(ID_ATTRIBUTE);
        // Remove old child
        i.remove();
        // Put new child into the map
        ArrayList<Element> elements = id2elements.get(id);
        if (elements == null) {
          elements = new ArrayList<>(2);
          id2elements.put(id, elements);
        }
        elements.add(oldChild);
      }
      else {
        throw new InvalidDataException("unknown element : " + oldChild.getName());
      }
    }

    for (String id : id2elements.keySet()) {
      Element actionElement = new Element(ACTION);
      actionElement.setAttribute(ID_ATTRIBUTE, id);
      ArrayList<Element> elements = id2elements.get(id);
      for (Element newChild : elements) {
        actionElement.addContent(newChild);
      }
      keymapElement.addContent(actionElement);
    }
  }
}
