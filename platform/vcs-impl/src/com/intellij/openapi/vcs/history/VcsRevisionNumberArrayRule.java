/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListByDateComparator;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.VcsRevisionNumberAware;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vcs.VcsDataKeys.*;

/**
 * {@link VcsDataKeys#VCS_REVISION_NUMBERS}
 */
@ApiStatus.Internal
public final class VcsRevisionNumberArrayRule {
  public static VcsRevisionNumber @Nullable [] getData(@NotNull DataMap dataProvider) {
    List<VcsRevisionNumber> revisionNumbers = getRevisionNumbers(dataProvider);

    return !ContainerUtil.isEmpty(revisionNumbers) ? revisionNumbers.toArray(new VcsRevisionNumber[0]) : null;
  }

  private static @Nullable List<VcsRevisionNumber> getRevisionNumbers(@NotNull DataMap dataProvider) {
    VcsRevisionNumber revisionNumber = dataProvider.get(VCS_REVISION_NUMBER);
    if (revisionNumber != null) {
      return Collections.singletonList(revisionNumber);
    }

    ChangeList[] changeLists = dataProvider.get(CHANGE_LISTS);
    if (changeLists != null && changeLists.length > 0) {
      List<CommittedChangeList> committedChangeLists = new ArrayList<>(ContainerUtil.findAll(changeLists, CommittedChangeList.class));

      if (!committedChangeLists.isEmpty()) {
        ContainerUtil.sort(committedChangeLists, CommittedChangeListByDateComparator.DESCENDING);

        return ContainerUtil.mapNotNull(committedChangeLists, CommittedChangeListToRevisionNumberFunction.INSTANCE);
      }
    }

    VcsFileRevision[] fileRevisions = dataProvider.get(VCS_FILE_REVISIONS);
    if (fileRevisions != null) {
      List<VcsFileRevision> revisions = ContainerUtil.filter(fileRevisions, r -> r != VcsFileRevision.NULL);
      if (!revisions.isEmpty()) {
        return ContainerUtil.mapNotNull(fileRevisions, FileRevisionToRevisionNumberFunction.INSTANCE);
      }
    }

    return null;
  }

  private static final class CommittedChangeListToRevisionNumberFunction implements Function<CommittedChangeList, VcsRevisionNumber> {
    private static final CommittedChangeListToRevisionNumberFunction INSTANCE = new CommittedChangeListToRevisionNumberFunction();

    /**
     * TODO: Currently we do not return just "new VcsRevisionNumber.Long(changeList.getNumber())" for change lists which are not
     * TODO: explicitly VcsRevisionNumberAware as a lot of unnecessary objects will be created because VCS_REVISION_NUMBERS value is
     * TODO: obtained in CopyRevisionNumberAction.update().
     * <p/>
     * TODO: Decide if this is reasonable.
     */
    @Override
    public VcsRevisionNumber fun(CommittedChangeList changeList) {
      return changeList instanceof VcsRevisionNumberAware ? ((VcsRevisionNumberAware)changeList).getRevisionNumber() : null;
    }
  }

  private static final class FileRevisionToRevisionNumberFunction implements Function<VcsFileRevision, VcsRevisionNumber> {
    private static final FileRevisionToRevisionNumberFunction INSTANCE = new FileRevisionToRevisionNumberFunction();

    @Override
    public VcsRevisionNumber fun(VcsFileRevision fileRevision) {
      return fileRevision.getRevisionNumber();
    }
  }
}
