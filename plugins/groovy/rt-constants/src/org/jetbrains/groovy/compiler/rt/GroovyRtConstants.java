/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.groovy.compiler.rt;

/**
 * @author nik
 */
public class GroovyRtConstants {
  public static final String PATCHERS = "patchers";
  public static final String ENCODING = "encoding";
  public static final String OUTPUTPATH = "outputpath";
  public static final String FINAL_OUTPUTPATH = "final_outputpath";
  public static final String END = "end";
  public static final String SRC_FILE = "src_file";
  public static final String COMPILED_START = "%%c";
  public static final String COMPILED_END = "/%c";
  public static final String TO_RECOMPILE_START = "%%rc";
  public static final String TO_RECOMPILE_END = "/%rc";
  public static final String MESSAGES_START = "%%m";
  public static final String MESSAGES_END = "/%m";
  public static final String SEPARATOR = "#%%#%%%#%%%%%%%%%#";
  //public static final Controller ourController = initController();
  public static final String PRESENTABLE_MESSAGE = "@#$%@# Presentable:";
  public static final String CLEAR_PRESENTABLE = "$@#$%^ CLEAR_PRESENTABLE";
  public static final String NO_GROOVY = "Cannot compile Groovy files: no Groovy library is defined";
}
