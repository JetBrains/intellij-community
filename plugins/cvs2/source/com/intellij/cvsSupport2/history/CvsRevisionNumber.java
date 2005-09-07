package com.intellij.cvsSupport2.history;

import com.intellij.cvsSupport2.config.DateOrRevisionSettings;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * author: lesya
 */
public class CvsRevisionNumber implements VcsRevisionNumber {

  private final String myStringRepresentation;
  @Nullable
  private final int[] mySubRevisions;
  private final DateOrRevisionSettings myDateOrRevision;

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

  public CvsRevisionNumber(@NotNull DateOrRevisionSettings dateOrRevision) {
    this(dateOrRevision.asString(), dateOrRevision);
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

  private CvsRevisionNumber(String sringRepresentation, @NotNull int[] subRevisions) {
    myStringRepresentation = sringRepresentation;
    mySubRevisions = subRevisions;
    myDateOrRevision = null;
  }

  private CvsRevisionNumber(String stringRepresentation, @NotNull DateOrRevisionSettings dateOrRevision) {
    myStringRepresentation = stringRepresentation;
    mySubRevisions = null;
    myDateOrRevision = dateOrRevision;
  }

  protected int compareToCvsRevisionNumber(CvsRevisionNumber other) {
    if (mySubRevisions != null && other.mySubRevisions != null) {
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
    }
    else if (myDateOrRevision != null) {
      if (other.myDateOrRevision != null) {
        return myDateOrRevision.compareTo(other.myDateOrRevision);
      }
    }
    return 0;

  }

  public int compareTo(VcsRevisionNumber o) {
    if (o instanceof CvsRevisionNumber) {
      CvsRevisionNumber other = ((CvsRevisionNumber)o);
      return -other.compareToCvsRevisionNumber(this);
    }
    else {
      return 0;
    }
  }

  private int getSubRevisionAt(int i) {
    if (mySubRevisions != null && i < mySubRevisions.length) {
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

  public CvsRevisionNumber removeTailVersions(final int i) {
    if (mySubRevisions != null && mySubRevisions.length < i) return this;
    final int[] resultSubVersions = new int[mySubRevisions.length - i];
    System.arraycopy(mySubRevisions, 0, resultSubVersions, 0, resultSubVersions.length);
    return new CvsRevisionNumber(createStringRepresentation(resultSubVersions), resultSubVersions);
  }

  private static String createStringRepresentation(final int[] versions) {
    return StringUtil.join(versions, ".");
  }

  public CvsRevisionNumber addTailVersions(final int[] versions) {
    if (mySubRevisions != null) {
      final int[] resultSubVersions = new int[mySubRevisions.length + versions.length];
      System.arraycopy(mySubRevisions, 0, resultSubVersions, 0, mySubRevisions.length);
      System.arraycopy(versions, 0, resultSubVersions, mySubRevisions.length, versions.length);
      return new CvsRevisionNumber(createStringRepresentation(resultSubVersions), resultSubVersions);
    }
    else {
      return this;
    }

  }

  public boolean isTopVersion() {
    if (mySubRevisions != null) {
      return mySubRevisions.length <= 2;
    }
    else {
      return false;
    }
  }

  public RevisionOrDate createVirsionInfo() {
    if (myDateOrRevision != null) {
      return RevisionOrDateImpl.createOn(myDateOrRevision);
    }
    else {
      return new SimpleRevision(asString());
    }
  }
}
