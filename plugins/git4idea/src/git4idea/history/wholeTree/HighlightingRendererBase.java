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
package git4idea.history.wholeTree;

import java.util.ArrayList;
import java.util.List;

/**
* @author irengrig
*         Date: 7/1/11
*         Time: 2:37 PM
*/
abstract class HighlightingRendererBase {
  private final List<String> mySearchContext;

  HighlightingRendererBase() {
    mySearchContext = new ArrayList<String>();
  }

  public void setSearchContext(final List<String> list) {
    mySearchContext.clear();
    mySearchContext.addAll(list);
  }

  protected abstract void usual(final String s);
  protected abstract void highlight(final String s);

  void tryHighlight(String text) {
    final String lower = text.toLowerCase();
    int idxFrom = 0;
    while (idxFrom < text.length()) {
      boolean adjusted = false;
      int adjustedIdx = text.length() + 1;
      int adjLen = 0;
      for (String word : mySearchContext) {
        final int next = lower.indexOf(word, idxFrom);
        if ((next != -1) && (adjustedIdx > next)) {
          adjustedIdx = next;
          adjLen = word.length();
          adjusted = true;
        }
      }
      if (adjusted) {
        if (idxFrom != adjustedIdx) {
          usual(text.substring(idxFrom, adjustedIdx));
        }
        idxFrom = adjustedIdx + adjLen;
        highlight(text.substring(adjustedIdx, idxFrom));
        continue;
      }
      usual(text.substring(idxFrom));
      return;
    }
  }

  public boolean isEmpty() {
    return mySearchContext == null || mySearchContext.isEmpty();
  }
}
