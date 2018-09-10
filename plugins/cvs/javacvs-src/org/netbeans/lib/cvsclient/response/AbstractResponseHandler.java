// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.netbeans.lib.cvsclient.response;

/**
 * @author  Thomas Singer
 */
abstract class AbstractResponseHandler
        implements IResponseHandler {

	// Implemented ============================================================

	@Override
        public final void processOkResponse(IResponseServices responseServices) {
		responseServices.getEventSender().notifyTerminationListeners(false);
	}

	@Override
        public final void processErrorResponse(byte[] message, IResponseServices responseServices) {
		responseServices.getEventSender().notifyMessageListeners(message, true, false);
		responseServices.getEventSender().notifyTerminationListeners(true);
	}
}
