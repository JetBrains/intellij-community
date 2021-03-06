// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader;

public interface EventLogUploaderOptions {
  String IDE_TOKEN = "--ide-token";

  String RECORDER_OPTION = "--recorder";
  String LOGS_OPTION = "--files";

  String DEVICE_OPTION = "--device";
  String BUCKET_OPTION = "--bucket";

  String URL_OPTION = "--url";
  String PRODUCT_OPTION = "--product";
  String PRODUCT_VERSION_OPTION = "--product-version";
  String USER_AGENT_OPTION = "--user-agent";
  String INTERNAL_OPTION = "--internal";
  String TEST_OPTION = "--test";
  String EAP_OPTION = "--eap";
}
