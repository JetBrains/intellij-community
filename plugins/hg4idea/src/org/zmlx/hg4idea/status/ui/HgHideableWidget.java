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
package org.zmlx.hg4idea.status.ui;

/**
 * Interface for {@link HgIncomingOutgoingWidget} that allows it to show/hide or update itself depending on the situation:
 * if incoming/outgoing changes need to be checked or not ({@link org.zmlx.hg4idea.HgVcs#INCOMING_OUTGOING_CHECK_TOPIC}).
 */
public interface HgHideableWidget {

  void show();

  void hide();

  void update();
}
