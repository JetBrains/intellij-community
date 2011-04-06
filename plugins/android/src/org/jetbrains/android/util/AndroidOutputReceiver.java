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

package org.jetbrains.android.util;

import com.android.ddmlib.MultiLineReceiver;
import com.intellij.openapi.diagnostic.Logger;

import java.io.UnsupportedEncodingException;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Sep 18, 2009
 * Time: 6:43:37 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AndroidOutputReceiver extends MultiLineReceiver {
  private static final String BAD_ACCESS_ERROR = "Bad address (14)";
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.util.AndroidOutputReceiver");
  private boolean myTryAgain;

  @Override
  public void processNewLines(String[] lines) {
    if (!myTryAgain) {
      for (String line : lines) {
        line = decodeIso8859_1(line);
        processNewLine(line);
        if (line.indexOf(BAD_ACCESS_ERROR) >= 0) {
          myTryAgain = true;
          break;
        }
      }
    }
  }

  public boolean isTryAgain() {
    return myTryAgain;
  }

  public void invalidate() {
    myTryAgain = false;
  }

  protected abstract void processNewLine(String line);

  private static String decodeIso8859_1(String text) {
    try {
      return new String(text.getBytes("ISO8859_1"), "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      LOG.error("Error while fix encoding", e);
      return text;
    }
  }
}
