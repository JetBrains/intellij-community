/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.remoteServer.statistics;

import com.intellij.featureStatistics.ApplicabilityFilter;
import com.intellij.featureStatistics.FeatureDescriptor;
import com.intellij.featureStatistics.GroupDescriptor;
import com.intellij.featureStatistics.ProductivityFeaturesProvider;
import com.intellij.remoteServer.util.CloudBundle;

import java.util.Collections;

public class CloudFeaturesProvider extends ProductivityFeaturesProvider {
  public static final String CLOUDS_GROUP_ID = "clouds";
  public static final String UPLOAD_SSH_KEY_FEATURE_ID = "upload.ssh.key";

  @Override
  public FeatureDescriptor[] getFeatureDescriptors() {
    return new FeatureDescriptor[]{new FeatureDescriptor(UPLOAD_SSH_KEY_FEATURE_ID,
                                                         CLOUDS_GROUP_ID,
                                                         "UploadSshKey.html",
                                                         CloudBundle.getText("upload.ssh.key.display.name"),
                                                         0,
                                                         0,
                                                         Collections.<String>emptySet(),
                                                         0,
                                                         this)};
  }

  @Override
  public GroupDescriptor[] getGroupDescriptors()
  {
    return new GroupDescriptor[] {
      new GroupDescriptor(CLOUDS_GROUP_ID, CloudBundle.getText("group.display.name"))
    };
  }

  @Override
  public ApplicabilityFilter[] getApplicabilityFilters() {
    return new ApplicabilityFilter[0];
  }
}
