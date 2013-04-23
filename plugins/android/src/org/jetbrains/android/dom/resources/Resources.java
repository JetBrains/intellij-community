/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

  List<Plurals> getPluralss();
  Plurals addPlurals();

  List<ScalarResourceElement> getColors();
  ScalarResourceElement addColor();

  List<ScalarResourceElement> getDrawables();
  ScalarResourceElement addDrawable();

  List<ScalarResourceElement> getDimens();
  ScalarResourceElement addDimen();

  List<Style> getStyles();
  Style addStyle();

  List<StringArray> getArrays();
  StringArray addArray();

  List<BoolElement> getBools();
  BoolElement addBool();

  List<IntegerElement> getIntegers();
  IntegerElement addInteger();

  List<FractionElement> getFractions();
  FractionElement addFraction();

  List<IntegerArray> getIntegerArrays();
  IntegerArray addIntegerArray();

  List<StringArray> getStringArrays();
  StringArray addStringArray();

  List<DeclareStyleable> getDeclareStyleables();
  DeclareStyleable addDeclareStyleable();

  List<Attr> getAttrs();
  Attr addAttr();

  List<Item> getItems();
  Item addItem();

  List<AndroidDomElement> getEatComments();
}
