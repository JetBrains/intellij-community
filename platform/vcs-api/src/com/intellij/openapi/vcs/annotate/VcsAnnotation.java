/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;

import java.util.HashMap;
import java.util.Map;

/**
 * @author irengrig
 *         Date: 3/10/11
 *         Time: 4:22 PM
 */
public class VcsAnnotation {
  private final FilePath myFilePath;
  private final VcsLineAnnotationData myBasicAnnotation;
  private final Map<Object, VcsLineAnnotationData> myAdditionalAnnotations;
  private final Map<VcsRevisionNumber, VcsFileRevision> myCachedOtherRevisions;
  private final VcsRevisionNumber myLastRevision;
//  private final VcsAbstractHistorySession myRelatedHistorySession;

  public VcsAnnotation(FilePath filePath, VcsLineAnnotationData basicAnnotation, VcsRevisionNumber lastRevision) {
    myBasicAnnotation = basicAnnotation;
    myLastRevision = lastRevision;
    myAdditionalAnnotations = new HashMap<>();
    myCachedOtherRevisions = new HashMap<>();
    myFilePath = filePath;
  }

  public void addAnnotation(final Object o, final VcsLineAnnotationData vcsLineAnnotationData) {
    myAdditionalAnnotations.put(o, vcsLineAnnotationData);
  }

  public void addCachedRevision(final VcsRevisionNumber number, final VcsFileRevision revision) {
    myCachedOtherRevisions.put(number, revision);
  }

  public void addCachedOtherRevisions(final Map<VcsRevisionNumber, VcsFileRevision> revisions) {
    myCachedOtherRevisions.putAll(revisions);
  }

  public FilePath getFilePath() {
    return myFilePath;
  }

  public VcsLineAnnotationData getBasicAnnotation() {
    return myBasicAnnotation;
  }

  public Map<Object, VcsLineAnnotationData> getAdditionalAnnotations() {
    return myAdditionalAnnotations;
  }

  public Map<VcsRevisionNumber, VcsFileRevision> getCachedOtherRevisions() {
    return myCachedOtherRevisions;
  }

  public VcsRevisionNumber getFirstRevision() {
    return myLastRevision;
  }
}

