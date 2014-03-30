package com.intellij.openapi.diagnostic;

import com.intellij.util.ArrayUtil;
import com.intellij.util.Base64Converter;
import com.intellij.util.PathUtilRt;

import java.io.UnsupportedEncodingException;

public class Attachment {
  private final String myPath;
  private final byte[] myBytes;
  private boolean myIncluded = true;
  private final String myDisplayText;

  public Attachment(String path, String content) {
    myPath = path;
    myDisplayText = content;
    myBytes = getBytes(content);
  }

  public Attachment(String path, byte[] bytes, String displayText) {
    myPath = path;
    myBytes = bytes;
    myDisplayText = displayText;
  }

  public static byte[] getBytes(String content) {
    try {
      return content.getBytes("UTF-8");
    }
    catch (UnsupportedEncodingException ignored) {
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }
  }

  public String getDisplayText() {
    return myDisplayText;
  }

  public String getPath() {
    return myPath;
  }

  public String getName() {
    return PathUtilRt.getFileName(myPath);
  }

  public String getEncodedBytes() {
    return Base64Converter.encode(myBytes);
  }

  public boolean isIncluded() {
    return myIncluded;
  }

  public void setIncluded(Boolean included) {
    myIncluded = included;
  }
}
