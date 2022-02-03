// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.options;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.Objects;

public final class LanguageOptions implements Cloneable {
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
    addBlankBefore = false;
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

  public boolean isAddBlankBefore() {
    return addBlankBefore;
  }

  public void setAddBlankBefore(boolean addBlankBefore) {
    this.addBlankBefore = addBlankBefore;
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
    DefaultJDOMExternalizer.write(this, element, new DifferenceFilter<>(this, DEFAULT_SETTINGS_HOLDER));
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
    if(addBlankBefore != that.addBlankBefore){
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
    if (!Objects.equals(filler, that.filler)) {
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
    result = 29 * result + (addBlankBefore ? 1 : 0);
    result = 29 * result + fileLocation;
    return result;
  }

  public String toString() {
    return "LanguageOptions" +
           ", fileTypeOverride=" + fileTypeOverride +
           ", relativeBefore=" + relativeBefore +
           ", addBlankAfter=" + addBlankAfter +
           ", addBlankBefore=" + addBlankBefore +
           ", fileLocation=" + fileLocation +
           ", block=" + block +
           ", separateBefore=" + separateBefore +
           ", separateAfter=" + separateAfter +
           ", prefixLines=" + prefixLines +
           ", lenBefore=" + lenBefore +
           ", lenAfter=" + lenAfter +
           ", box=" + box +
           ", filler=" + filler +
           '}';
  }

  @Override
  public LanguageOptions clone() throws CloneNotSupportedException {
    return (LanguageOptions)super.clone();
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

  public void setNotice(String notice){this.notice = notice;}

  public int fileTypeOverride;
  public boolean relativeBefore;
  public boolean addBlankAfter;
  public boolean addBlankBefore;
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
  public String notice;
}