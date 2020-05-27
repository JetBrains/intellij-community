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
package com.intellij.cvsSupport2.actions;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsWatch.ui.WatcherDialog;
import com.intellij.openapi.vcs.actions.VcsContext;
import org.netbeans.lib.cvsclient.command.Watch;
import org.netbeans.lib.cvsclient.command.watch.WatchMode;

/**
 * author: lesya
 */
public class WatchRemoveAction extends AbstractWatchAction {
  @Override
  protected String getTitle(VcsContext context) {
    return CvsBundle.message("operation.name.watching.remove");
  }

  @Override
  protected WatcherDialog createDialog(CvsConfiguration configuration, VcsContext context) {
    return new WatcherDialog(configuration.WATCHERS.get(configuration.REMOVE_WATCH_INDEX), getTitle(context));
  }

  @Override
  protected WatchMode getWatchOperation() {
    return WatchMode.REMOVE;
  }

  @Override
  protected void saveWatch(CvsConfiguration configuration, Watch watch) {
    configuration.REMOVE_WATCH_INDEX = configuration.WATCHERS.indexOf(watch);
  }
}
