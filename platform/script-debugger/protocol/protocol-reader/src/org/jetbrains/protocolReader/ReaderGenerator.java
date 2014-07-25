// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.protocolReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ReaderGenerator {
  protected static void mainImpl(String[] args, GenerateConfiguration configuration) throws IOException {
    FileUpdater fileUpdater = new FileUpdater(FileSystems.getDefault().getPath(parseArgs(args).outputDirectory(),
                                                                               configuration.getPackageName().replace('.',
                                                                                                                      File.separatorChar),
                                                                               configuration.getClassName() + ".java"));
    generateImpl(configuration, fileUpdater.builder);
    fileUpdater.update();
  }

  protected static class GenerateConfiguration {
    private final String packageName;
    private final String className;
    private final DynamicReader<?> parser;
    private final Collection<GeneratedCodeMap> basePackagesMap;

    public GenerateConfiguration(String packageName, String className, DynamicReader<?> parser) {
      this(packageName, className, parser, Collections.<GeneratedCodeMap>emptyList());
    }

    public GenerateConfiguration(String packageName, String className, DynamicReader<?> parser, Collection<GeneratedCodeMap> basePackagesMap) {
      this.packageName = packageName;
      this.className = className;
      this.parser = parser;
      this.basePackagesMap = basePackagesMap;
    }

    public String getPackageName() {
      return packageName;
    }

    public String getClassName() {
      return className;
    }

    public DynamicReader<?> getParser() {
      return parser;
    }

    public Collection<GeneratedCodeMap> getBasePackagesMap() {
      return basePackagesMap;
    }
  }

  private interface Params {
    String outputDirectory();
  }

  private static Params parseArgs(String[] args) {
    final StringParam outputDirParam = new StringParam();

    Map<String, StringParam> paramMap = new HashMap<>(3);
    paramMap.put("output-dir", outputDirParam);

    for (String arg : args) {
      if (!arg.startsWith("--")) {
        throw new IllegalArgumentException("Unrecognized param: " + arg);
      }
      int equalsPos = arg.indexOf('=', 2);
      String key;
      String value;
      if (equalsPos == -1) {
        key = arg.substring(2).trim();
        value = null;
      } else {
        key = arg.substring(2, equalsPos).trim();
        value = arg.substring(equalsPos + 1).trim();
      }
      ParamListener paramListener = paramMap.get(key);
      if (paramListener == null) {
        throw new IllegalArgumentException("Unrecognized param name: " + key);
      }
      try {
        paramListener.setValue(value);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Failed to set value of " + key, e);
      }
    }
    for (Map.Entry<String, StringParam> en : paramMap.entrySet()) {
      if (en.getValue().getValue() == null) {
        throw new IllegalArgumentException("Parameter " + en.getKey() + " should be set");
      }
    }

    return new Params() {
      @Override
      public String outputDirectory() {
        return outputDirParam.getValue();
      }
    };
  }

  private interface ParamListener {
    void setValue(String value);
  }

  private static class StringParam implements ParamListener {
    private String value;

    @Override
    public void setValue(String value) {
      if (value == null) {
        throw new IllegalArgumentException("Argument with value expected");
      }
      if (this.value != null) {
        throw new IllegalArgumentException("Argument value already set");
      }
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  protected static GeneratedCodeMap buildParserMap(GenerateConfiguration configuration) {
    return generateImpl(configuration, new StringBuilder());
  }

  private static GeneratedCodeMap generateImpl(GenerateConfiguration configuration, StringBuilder stringBuilder) {
    return configuration.getParser().generateStaticReader(stringBuilder,
                                                          configuration.getPackageName(), configuration.getClassName(),
                                                          configuration.getBasePackagesMap());
  }
}
