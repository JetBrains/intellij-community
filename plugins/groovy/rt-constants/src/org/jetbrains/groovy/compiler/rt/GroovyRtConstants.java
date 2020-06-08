// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.groovy.compiler.rt;

public final class GroovyRtConstants {
  public static final String PATCHERS = "patchers";
  public static final String ENCODING = "encoding";
  public static final String OUTPUTPATH = "outputpath";
  public static final String FINAL_OUTPUTPATH = "final_outputpath";
  public static final String END = "end";
  public static final String SRC_FILE = "src_file";
  public static final String COMPILED_START = "%%c";
  public static final String COMPILED_END = "/%c";
  public static final String MESSAGES_START = "%%m";
  public static final String MESSAGES_END = "/%m";
  public static final String SEPARATOR = "#%%#%%%#%%%%%%%%%#";
  //public static final Controller ourController = initController();
  public static final String PRESENTABLE_MESSAGE = "@#$%@# Presentable:";
  public static final String CLEAR_PRESENTABLE = "$@#$%^ CLEAR_PRESENTABLE";
  public static final String NO_GROOVY = "Cannot compile Groovy files: no Groovy library is defined";
  public static final String OPTIMIZE = "optimize";
  public static final String GROOVYC_STUB_GENERATION_FAILED = "Groovyc stub generation failed";

  public static final String JAVAC_COMPLETED = "Javac completed";
  public static final String BUILD_ABORTED = "Build aborted";

  /**
   * Older version of groovyc generated malformed annotations in stubs, so give a possibility to skip those
   */
  public static final String GROOVYC_LEGACY_REMOVE_ANNOTATIONS = "groovyc.remove.annotations.for.stub.generation";

  public static final String GROOVYC_ASM_RESOLVING_ONLY = "groovyc.asm.resolving.only";
  public static final String GROOVYC_CONFIG_SCRIPT = "groovyc.config.script";
  public static final String GROOVY_TARGET_BYTECODE = "groovy.target.bytecode";
}
