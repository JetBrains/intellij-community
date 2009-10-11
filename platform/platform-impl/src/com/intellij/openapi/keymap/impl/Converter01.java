/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author Vladimir Kondratyev
 */
public class Converter01{

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
   * @param keymapElement XML element that corresponds to "keymap" tag.
   */
  public static void convert(Element keymapElement) throws InvalidDataException{
    if(!KEY_MAP.equals(keymapElement.getName())){
      throw new IllegalArgumentException(UNKNOWN_ELEMENT + keymapElement);
    }
    String version=keymapElement.getAttributeValue(VERSION);
    if(version!=null){
      throw new InvalidDataException(UNKNOWN_VERSION + version);
    }

    // Add version

    keymapElement.setAttribute(VERSION,Integer.toString(1));

    // disableMnemonics -> disable-mnemonics

    boolean disableMnemonics=Boolean.valueOf(DISABLE_MNEMONICS).booleanValue();
    keymapElement.removeAttribute(DISABLE_MNEMONICS);
    keymapElement.setAttribute(DISABLE_MNEMONICS_ATTRIBUTE,Boolean.toString(disableMnemonics));

    // Now we have to separate all shortcuts by action's ID and convert binding to keyboard-shortcut

    String name=keymapElement.getAttributeValue(NAME_ATTRIBUTE);
    if(name==null){
      throw new InvalidDataException("Attribute 'name' of <keymap> must be specified");
    }
    HashMap id2elements=new HashMap();

    for(Iterator i=keymapElement.getChildren().iterator();i.hasNext();){
      Element oldChild=(Element)i.next();
      if(BINDING.equals(oldChild.getName())){ // binding -> keyboard-shortcut
        // Remove attribute "id"
        String id=oldChild.getAttributeValue(ID_ATTRIBUTE);
        if(id==null){
          throw new InvalidDataException("attribute 'id' must be specified");
        }
        // keystroke -> first-keystroke
        String keystroke=oldChild.getAttributeValue(KEYSTROKE_ATTRIBUTE);
        // suffix -> second-keystroke
        String suffix=oldChild.getAttributeValue(SUFFIX_ATTRIBUTE);
        if(keystroke!=null){
          Element newChild=new Element(KEYBOARD_SHORTCUT);
          newChild.setAttribute(FIRST_KEYSTROKE_ATTRIBUTE,keystroke);
          if(suffix!=null){
            newChild.setAttribute(SECOND_KEYSTROKE_ATTRIBUTE,suffix);
          }
          // Put new child into the map
          ArrayList elements=(ArrayList)id2elements.get(id);
          if(elements==null){
            elements=new ArrayList(2);
            id2elements.put(id,elements);
          }
          elements.add(newChild);
        }else{
          id2elements.put(id,new ArrayList(0));
        }
        // Remove old child
        i.remove();
      }else if(MOUSE_SHORTCUT.equals(oldChild.getName())){
        // Remove attribute "id"
        String id=oldChild.getAttributeValue(ID_ATTRIBUTE);
        if(id==null){
          throw new InvalidDataException("Attribute 'id' of <mouse-shortcut> must be specified; keymap name="+name);
        }
        oldChild.removeAttribute(ID_ATTRIBUTE);
        // Remove old child
        i.remove();
        // Put new child into the map
        ArrayList elements=(ArrayList)id2elements.get(id);
        if(elements==null){
          elements=new ArrayList(2);
          id2elements.put(id,elements);
        }
        elements.add(oldChild);
      }else{
        throw new InvalidDataException("unknown element : "+oldChild.getName());
      }
    }

    for(Iterator i=id2elements.keySet().iterator();i.hasNext();){
      String id=(String)i.next();
      Element actionElement=new Element(ACTION);
      actionElement.setAttribute(ID_ATTRIBUTE,id);
      ArrayList elements=(ArrayList)id2elements.get(id);
      for(Iterator j=elements.iterator();j.hasNext();){
        Element newChild=(Element)j.next();
        actionElement.addContent(newChild);
      }
      keymapElement.addContent(actionElement);
    }
  }
}
