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
package org.jetbrains.plugins.groovy.doc;

/**
 * @author Dmitry Krasilschikov
 */
public class GroovyDocConfiguration {
  public String OUTPUT_DIRECTORY = "";
  public String INPUT_DIRECTORY = "";
  public String WINDOW_TITLE = "";
  public String[] PACKAGES = new String[]{ALL_PACKAGES};

  public boolean OPTION_IS_USE = true;
  public boolean OPTION_IS_PRIVATE = true;
  public boolean OPEN_IN_BROWSER = true;

  public static final String ALL_PACKAGES = "**.*";
}
