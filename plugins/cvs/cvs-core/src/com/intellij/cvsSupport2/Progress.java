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
package com.intellij.cvsSupport2;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;

/**
 * author: lesya
 */
public abstract class Progress {
  public abstract void setText(String text);

  public static final Progress DEAF = new Progress() {
    public void setText(String text) {

    }
  };

  public static Progress create(){
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      return new Progress() {
        public void setText(String text) {
          progressIndicator.setText2(text);
        }
      };
    }
    else {
      return new Progress() {
        public void setText(String text) {
        }
      };
    }

  }
}
