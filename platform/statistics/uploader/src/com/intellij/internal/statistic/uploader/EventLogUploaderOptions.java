// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader;

public interface EventLogUploaderOptions {
  String IDE_TOKEN = "--ide-token";

  String RECORDER_OPTION = "--recorder";
  String LOGS_OPTION = "--files";

  String DEVICE_OPTION = "--device";
  String BUCKET_OPTION = "--bucket";
  String MACHINE_ID_OPTION = "--machine-id";
  String ID_REVISION_OPTION = "--id-revision-option";

  String URL_OPTION = "--url";
  String PRODUCT_OPTION = "--product";
  String PRODUCT_VERSION_OPTION = "--product-version";
  String USER_AGENT_OPTION = "--user-agent";
  String EXTRA_HEADERS = "--extra-headers";
  String INTERNAL_OPTION = "--internal";
  String TEST_OPTION = "--test";
  String EAP_OPTION = "--eap";
}
