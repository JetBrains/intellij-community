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
package com.intellij.openapi.progress.util;


public class CommandLineProgress extends ProgressIndicatorBase{
  public void setText(String text) {
    if (getText().equals(text)) return;
    super.setText(text);
    System.out.println(getTextToPrint());
  }

  public void setFraction(double fraction) {
    String oldText = getTextToPrint();
    super.setFraction(fraction);
    String newText = getTextToPrint();
    if (!newText.equals(oldText)){
      System.out.println(newText);
    }
  }

  private String getTextToPrint(){
    if (getFraction() > 0){
      return getText() + " " + (int)(getFraction() * 100 + 0.5) + "%";
    }
    else{
      return getText();
    }
  }
}
