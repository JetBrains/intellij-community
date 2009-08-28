package com.intellij.ui.growl;

import com.sun.jna.NativeLong;

/**
 * @author spleaner
 */
public class ID extends NativeLong {

  static ID fromLong(long value) {
    return new ID(value);
  }

  // for JNA
  public ID() {
    super();
  }

  protected ID(long value) {
    super(value);
  }

  protected ID(ID anotherID) {
    this(anotherID.longValue());
  }

  @Override
  public String toString() {
    return String.format("[ID 0x%x]", longValue()); //$NON-NLS-1$
  }

  public boolean isNull() {
    return longValue() == 0;
  }
}
