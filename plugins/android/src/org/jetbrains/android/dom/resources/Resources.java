package org.jetbrains.android.dom.resources;

import com.intellij.util.xml.DefinesXml;
import org.jetbrains.android.dom.AndroidDomElement;

import java.util.List;

/**
 * @author yole
 */
@DefinesXml
public interface Resources extends AndroidDomElement {
  List<StringElement> getStrings();
  StringElement addString();

  List<ResourceElement> getColors();
  ResourceElement addColor();

  List<ResourceElement> getDrawables();
  ResourceElement addDrawable();

  List<ResourceElement> getDimens();
  ResourceElement addDimen();

  List<Style> getStyles();
  Style addStyle();

  List<StringArray> getStringArrays();
  StringArray addStringArray();

  List<DeclareStyleable> getDeclareStyleables();
  List<Attr> getAttrs();

  List<Item> getItems();
  Item addItem();

  List<AndroidDomElement> getEatComments();
}
