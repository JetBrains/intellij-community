// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader;

public interface EventLogUploaderOptions {
  String RECORDER_OPTION = "--recorder";
  String DIRECTORY_OPTION = "--dir";

  String DEVICE_OPTION = "--device";
  String BUCKET_OPTION = "--bucket";

  String URL_OPTION = "--url";
  String PRODUCT_OPTION = "--product";
  String INTERNAL_OPTION = "--internal";
  String TEST_OPTION = "--test";
}
