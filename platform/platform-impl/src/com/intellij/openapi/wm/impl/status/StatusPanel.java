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
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author peter
 */
class StatusPanel extends TextPanel {
  private boolean myLogMode;
  private String myLogMessage;
  private Date myLogTime;
  private final Alarm myLogAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  public void setLogMessage(String text) {
    myLogMessage = text;
    myLogTime = new Date();
  }

  public void updateText(boolean logMode, @Nullable String nonLogText) {
    myLogMode = logMode;

    if (logMode) {
      new Runnable() {
        @Override
        public void run() {
          setText(myLogMessage + " (" + StringUtil.decapitalize(DateFormatUtil.formatPrettyDateTime(myLogTime)) + ")");
          myLogAlarm.addRequest(this, 30000);
        }
      }.run();
    } else {
      setText(nonLogText);
      myLogAlarm.cancelAllRequests();
    }

  }

  public void hideLog() {
    if (myLogMode) {
      updateText(false, "");
    }
  }

  public void restoreLogIfNeeded() {
    myLogAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (StringUtil.isEmpty(getText())) {
          updateText(true, "");
        }
      }
    }, 300);
  }
}
