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

package com.maddyhome.idea.copyright.options;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class LanguageOptions implements Cloneable {
  public static final int NO_COPYRIGHT = 1;
  public static final int USE_TEMPLATE = 2;
  public static final int USE_TEXT = 3;

  public static final int MIN_SEPARATOR_LENGTH = 5;
  public static final int MAX_SEPARATOR_LENGTH = 300;
  public static final String DEFAULT_FILLER = " ";
  
  private static final LanguageOptions DEFAULT_SETTINGS_HOLDER = new LanguageOptions();

  public LanguageOptions() {
    setBlock(true);
    setPrefixLines(true);
    setSeparateBefore(false);
    setLenBefore(80);
    setSeparateAfter(false);
    setLenAfter(80);
    setBox(false);
    setFiller(DEFAULT_FILLER);

    fileTypeOverride = USE_TEMPLATE;
    relativeBefore = true;
    addBlankAfter = true;
    fileLocation = 1;
  }


  public int getFileTypeOverride() {
    return fileTypeOverride;
  }

  public void setFileTypeOverride(int fileTypeOverride) {
    this.fileTypeOverride = fileTypeOverride;
  }

  public boolean isRelativeBefore() {
    return relativeBefore;
  }

  public void setRelativeBefore(boolean relativeBefore) {
    this.relativeBefore = relativeBefore;
  }

  public boolean isAddBlankAfter() {
    return addBlankAfter;
  }

  public void setAddBlankAfter(boolean addBlankAfter) {
    this.addBlankAfter = addBlankAfter;
  }

  public int getFileLocation() {
    return fileLocation;
  }

  public void setFileLocation(int fileLocation) {
    this.fileLocation = fileLocation;
  }


  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element, new DifferenceFilter<>(this, DEFAULT_SETTINGS_HOLDER));
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final LanguageOptions that = (LanguageOptions)o;

    if (addBlankAfter != that.addBlankAfter) {
      return false;
    }
    if (fileLocation != that.fileLocation) {
      return false;
    }
    if (fileTypeOverride != that.fileTypeOverride) {
      return false;
    }
    if (relativeBefore != that.relativeBefore) {
      return false;
    }
    if (block != that.block) {
      return false;
    }
    if (box != that.box) {
      return false;
    }
    if (filler != that.filler) {
      return false;
    }
    if (lenAfter != that.lenAfter) {
      return false;
    }
    if (lenBefore != that.lenBefore) {
      return false;
    }
    if (prefixLines != that.prefixLines) {
      return false;
    }
    if (separateAfter != that.separateAfter) {
      return false;
    }
    return separateBefore == that.separateBefore;

  }

  public int hashCode() {
    int result;
    result = (block ? 1 : 0);
    result = 29 * result + (separateBefore ? 1 : 0);
    result = 29 * result + (separateAfter ? 1 : 0);
    result = 29 * result + (prefixLines ? 1 : 0);
    result = 29 * result + lenBefore;
    result = 29 * result + lenAfter;
    result = 29 * result + (box ? 1 : 0);
    result = 29 * result + filler.hashCode();
    result = 29 * result + fileTypeOverride;
    result = 29 * result + (relativeBefore ? 1 : 0);
    result = 29 * result + (addBlankAfter ? 1 : 0);
    result = 29 * result + fileLocation;
    return result;
  }

  public String toString() {
    final StringBuffer sb = new StringBuffer();
    sb.append("LanguageOptions");

    sb.append(", fileTypeOverride=").append(fileTypeOverride);
    sb.append(", relativeBefore=").append(relativeBefore);
    sb.append(", addBlankAfter=").append(addBlankAfter);
    sb.append(", fileLocation=").append(fileLocation);
    sb.append(", block=").append(block);
    sb.append(", separateBefore=").append(separateBefore);
    sb.append(", separateAfter=").append(separateAfter);
    sb.append(", prefixLines=").append(prefixLines);
    sb.append(", lenBefore=").append(lenBefore);
    sb.append(", lenAfter=").append(lenAfter);
    sb.append(", box=").append(box);
    sb.append(", filler=").append(filler);
    sb.append('}');

    return sb.toString();
  }

  public LanguageOptions clone() throws CloneNotSupportedException {
    LanguageOptions res = (LanguageOptions)super.clone();
    return res;
  }

  public boolean isBlock() {
    return block;
  }

  public void setBlock(boolean block) {
    this.block = block;
  }

  public boolean isSeparateBefore() {
    return separateBefore;
  }

  public void setSeparateBefore(boolean separateBefore) {
    this.separateBefore = separateBefore;
  }

  public boolean isSeparateAfter() {
    return separateAfter;
  }

  public void setSeparateAfter(boolean separateAfter) {
    this.separateAfter = separateAfter;
  }

  public boolean isPrefixLines() {
    return prefixLines;
  }

  public void setPrefixLines(boolean prefixLines) {
    this.prefixLines = prefixLines;
  }

  public int getLenBefore() {
    return lenBefore;
  }

  public void setLenBefore(int lenBefore) {
    this.lenBefore = lenBefore;
  }

  public int getLenAfter() {
    return lenAfter;
  }

  public void setLenAfter(int lenAfter) {
    this.lenAfter = lenAfter;
  }

  public boolean isBox() {
    return box;
  }

  public void setBox(boolean box) {
    this.box = box;
  }

  public String getFiller() {
    return filler;
  }

  public void setFiller(String filler) {
    this.filler = filler;
  }

  public boolean isTrim() {
    return trim;
  }

  public void setTrim(boolean trim) {
    this.trim = trim;
  }

  public int fileTypeOverride;
  public boolean relativeBefore;
  public boolean addBlankAfter;
  public int fileLocation;

  public boolean block;
  public boolean separateBefore;
  public boolean separateAfter;
  public boolean prefixLines;
  public int lenBefore;
  public int lenAfter;
  public boolean box;
  public String filler;
  public boolean trim;
}