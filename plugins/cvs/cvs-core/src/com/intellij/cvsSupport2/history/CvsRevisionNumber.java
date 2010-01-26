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
package com.intellij.cvsSupport2.history;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.DateOrRevisionSettings;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * author: lesya
 */
@SuppressWarnings({"CovariantCompareTo"})
public class CvsRevisionNumber implements VcsRevisionNumber {

  private final String myStringRepresentation;
  @Nullable
  private final int[] mySubRevisions;
  private final DateOrRevisionSettings myDateOrRevision;

  public static CvsRevisionNumber CURRENT =
    new CvsRevisionNumber(CvsBundle.message("current.file.revision.name"), ArrayUtil.EMPTY_INT_ARRAY) {
      protected int compareToCvsRevisionNumber(CvsRevisionNumber other) {
        return 1;
      }

      public int compareTo(VcsRevisionNumber o) {
        return 1;
      }
    };

  public static CvsRevisionNumber EMPTY = new CvsRevisionNumber("", ArrayUtil.EMPTY_INT_ARRAY) {
    protected int compareToCvsRevisionNumber(CvsRevisionNumber other) {
      return -1;
    }

    public int compareTo(VcsRevisionNumber o) {
      return -1;
    }
  };

  public CvsRevisionNumber(@NonNls String revision) {
    this(revision, parseRevisionString(revision));
  }

  public CvsRevisionNumber(@NotNull DateOrRevisionSettings dateOrRevision) {
    this(dateOrRevision.asString(), dateOrRevision);
  }

  private static int[] parseRevisionString(String revision) {
    int[] subRevisions;
    String[] stringSubRevisions = revision.split("\\.");
    subRevisions = new int[stringSubRevisions.length];

    int cnt = 0;
    for (int i = 0; i < stringSubRevisions.length; i++) {
      try {
        subRevisions[i] = Integer.parseInt(stringSubRevisions[i]);
        ++ cnt;
      }
      catch (NumberFormatException ex) {
        subRevisions[i] = 0;
      }
    }
    return (cnt == 0) ? new int[0] : subRevisions;
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

  public CvsRevisionNumber getPrevNumber() {
    if (mySubRevisions == null || mySubRevisions.length == 0) return this;

    final int length = mySubRevisions.length;

    final int[] resultSubVersions = new int[length];
    System.arraycopy(mySubRevisions, 0, resultSubVersions, 0, length);
    resultSubVersions[length - 1] -= 1;
    return new CvsRevisionNumber(createStringRepresentation(resultSubVersions), resultSubVersions);
  }

  private static String createStringRepresentation(final int[] versions) {
    return StringUtil.join(versions, ".");
  }

  public CvsRevisionNumber addTailVersions(final int... versions) {
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


  public DateOrRevisionSettings getDateOrRevision() {
    return myDateOrRevision;
  }

  @Nullable
  public int[] getSubRevisions() {
    return mySubRevisions;
  }
}
