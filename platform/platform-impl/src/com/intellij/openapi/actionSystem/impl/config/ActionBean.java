package com.intellij.openapi.actionSystem.impl.config;

import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;

@Tag(ActionManagerImpl.ACTION_ELEMENT_NAME)
public class ActionBean {
  @Attribute(ActionManagerImpl.ID_ATTR_NAME)
  public String id;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public KeyboardShortcutBean[] keyboardShortcuts;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public AddToGroupBean[] addToGroups;

  @Attribute(ActionManagerImpl.CLASS_ATTR_NAME)
  public String actionClass;

  @Attribute(ActionManagerImpl.ICON_ATTR_NAME)
  public String icon;


  @Attribute(ActionManagerImpl.POPUP_ATTR_NAME)
  public String isPopup;

  @Attribute(ActionManagerImpl.TEXT_ATTR_NAME)
  public String text;

  @Attribute(ActionManagerImpl.INTERNAL_ATTR_NAME)
  public boolean internal;

  @Attribute("use-shortcut-of")
  public boolean useShortcutOf;

  @Attribute(ActionManagerImpl.KEYMAP_ATTR_NAME)
  public String keymap;
}
