// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader;

public interface EventLogUploaderOptions {
  String IDE_TOKEN = "--ide-token";

  String RECORDERS_OPTION = "--recorders";
  String LOGS_OPTION = "--files-";
  String DEVICE_OPTION = "--device-";
  String BUCKET_OPTION = "--bucket-";
  String MACHINE_ID_OPTION = "--machine-";
  String ID_REVISION_OPTION = "--id-revision-";
  String ESCAPING_OPTION = "--escape-";

  String URL_OPTION = "--url";
  String PRODUCT_OPTION = "--product";
  String PRODUCT_VERSION_OPTION = "--product-version";
  String BASELINE_VERSION = "--baseline-version";
  String USER_AGENT_OPTION = "--user-agent";
  String EXTRA_HEADERS = "--extra-headers";
  String INTERNAL_OPTION = "--internal";
  String TEST_SEND_ENDPOINT = "--test-send-endpoint";
  String TEST_CONFIG = "--test-config";
  String EAP_OPTION = "--eap";
}
