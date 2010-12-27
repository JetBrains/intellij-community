/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.impl.IllegalStateProxy;
import com.intellij.openapi.vcs.impl.VcsEnvironmentsProxyCreator;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;

/**
 * @author irengrig
 *         Date: 12/17/10
 *         Time: 12:46 PM
 */
public class VcsActiveEnvironmentsProxy {
  private VcsActiveEnvironmentsProxy() {
  }

  public static AbstractVcs proxyVcs(final AbstractVcs vcs) {
    final ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(vcs.getProject());
    final VcsEnvironmentsProxyCreator proxyCreator = manager.getProxyCreator();
    if (proxyCreator == null) return vcs;

    final VcsKey key = vcs.getKeyInstanceMethod();
    final CheckinEnvironment checkinEnvironment = vcs.createCheckinEnvironment();
    final UpdateEnvironment updateEnvironment = vcs.createUpdateEnvironment();
    final RollbackEnvironment rollbackEnvironment = vcs.createRollbackEnvironment();

    if (checkinEnvironment != null && checkinEnvironment.equals(IllegalStateProxy.IDENTITY) ||
            updateEnvironment != null && updateEnvironment.equals(IllegalStateProxy.IDENTITY) ||
            rollbackEnvironment != null && rollbackEnvironment.equals(IllegalStateProxy.IDENTITY)) {
      return vcs;
    } else {
      final CheckinEnvironment proxedCheckin = proxyCreator.proxyCheckin(key, checkinEnvironment);
      final UpdateEnvironment proxedUpdate = proxyCreator.proxyUpdate(key, updateEnvironment);
      final RollbackEnvironment proxedRollback = proxyCreator.proxyRollback(key, rollbackEnvironment);
      vcs.setCheckinEnvironment(proxedCheckin);
      vcs.setUpdateEnvironment(proxedUpdate);
      vcs.setRollbackEnvironment(proxedRollback);
      return vcs;
    }
  }
}
