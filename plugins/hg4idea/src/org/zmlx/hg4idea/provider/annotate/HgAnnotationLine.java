// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.provider.annotate;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;

import java.util.EnumMap;

public class HgAnnotationLine {

  private EnumMap<HgAnnotation.FIELD, Object> fields =
    new EnumMap<>(HgAnnotation.FIELD.class);

  public HgAnnotationLine(String user, VcsRevisionNumber revision,
    String date, Integer line, String content) {
    fields.put(HgAnnotation.FIELD.USER, user);
    fields.put(HgAnnotation.FIELD.REVISION, revision);
    fields.put(HgAnnotation.FIELD.DATE, date);
    fields.put(HgAnnotation.FIELD.LINE, line);
    fields.put(HgAnnotation.FIELD.CONTENT, content);
  }

  public VcsRevisionNumber getVcsRevisionNumber() {
    return (VcsRevisionNumber) get(HgAnnotation.FIELD.REVISION);
  }

  public Object get(HgAnnotation.FIELD field) {
    return fields.get(field);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof HgAnnotationLine)) {
      return false;
    }
    HgAnnotationLine other = (HgAnnotationLine) obj;
    return fields.equals(other.fields);
  }

  @Override
  public int hashCode() {
    return fields.hashCode();
  }
}
