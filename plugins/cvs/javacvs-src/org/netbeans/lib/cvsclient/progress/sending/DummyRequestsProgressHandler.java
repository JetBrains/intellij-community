// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.netbeans.lib.cvsclient.progress.sending;

import org.netbeans.lib.cvsclient.request.IRequest;

/**
 * @author Thomas Singer
 */
public class DummyRequestsProgressHandler
        implements IRequestsProgressHandler {

	// Implemented ============================================================

	@Override
        public void requestSent(IRequest request) {
	}

}
