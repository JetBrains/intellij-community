package com.intellij.cvsSupport2.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;

/**
 * author: lesya
 */
public class CvsRevisionNumber implements VcsRevisionNumber {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.history.CvsRevisionNumber");

  private final String myStringRepresentation;
  private final int[] mySubRevisions;

  public static CvsRevisionNumber CURRENT = new CvsRevisionNumber("Current", new int[0]) {
    protected int compareToCvsRevisionNumber(CvsRevisionNumber other) {
      return 1;
    }

    public int compareTo(VcsRevisionNumber o) {
      return 1;
    }
  };

  public static CvsRevisionNumber EMPTY = new CvsRevisionNumber("", new int[0]) {
    protected int compareToCvsRevisionNumber(CvsRevisionNumber other) {
      return -1;
    }

    public int compareTo(VcsRevisionNumber o) {
      return -1;
    }
  };

  public CvsRevisionNumber(String revision) {
    this(revision, parseRevisionString(revision));
  }

  private static int[] parseRevisionString(String revision) {
    int[] subRevisions;
    String[] stringSubRevisions = revision.split("\\.");
    subRevisions = new int[stringSubRevisions.length];

    for (int i = 0; i < stringSubRevisions.length; i++) {
      try {
        subRevisions[i] = Integer.parseInt(stringSubRevisions[i]);
      }
      catch (NumberFormatException ex) {
        subRevisions[i] = 0;
      }
    }
    return subRevisions;
  }

  private CvsRevisionNumber(String sringRepresentation, int[] subRevisions) {
    myStringRepresentation = sringRepresentation;
    mySubRevisions = subRevisions;
  }

  protected int compareToCvsRevisionNumber(CvsRevisionNumber other) {
    if (other.mySubRevisions.length > mySubRevisions.length) {
      return -other.compareToCvsRevisionNumber(this);
    }

    for (int i = 0; i < mySubRevisions.length; i++) {
      int subRevision = mySubRevisions[i];
      int otherSubRevision = other.getSubRevisionAt(i);
      if (subRevision != otherSubRevision) {
        return subRevision > otherSubRevision ? 1 : -1;
      }

    }
    return 0;

  }

  public int compareTo(VcsRevisionNumber o) {
    LOG.assertTrue(o instanceof CvsRevisionNumber, o.getClass().toString());
    CvsRevisionNumber other = ((CvsRevisionNumber)o);
    return -other.compareToCvsRevisionNumber(this);
  }

  private int getSubRevisionAt(int i) {
    if (i < mySubRevisions.length) {
      return mySubRevisions[i];
    }
    else {
      return 0;
    }
  }

  public int hashCode() {
    return myStringRepresentation.hashCode();
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof CvsRevisionNumber)) {
      return false;
    }
    return myStringRepresentation.equals(((CvsRevisionNumber)obj).myStringRepresentation);
  }

  public String toString() {
    return myStringRepresentation;
  }

  public String asString() {
    return myStringRepresentation;
  }

}
