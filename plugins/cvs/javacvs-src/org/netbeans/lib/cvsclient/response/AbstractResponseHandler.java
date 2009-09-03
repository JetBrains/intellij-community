package org.netbeans.lib.cvsclient.response;

/**
 * @author  Thomas Singer
 */
abstract class AbstractResponseHandler
        implements IResponseHandler {

	// Implemented ============================================================

	public final void processOkResponse(IResponseServices responseServices) {
		responseServices.getEventSender().notifyTerminationListeners(false);
	}

	public final void processErrorResponse(byte[] message, IResponseServices responseServices) {
		responseServices.getEventSender().notifyMessageListeners(message, true, false);
		responseServices.getEventSender().notifyTerminationListeners(true);
	}
}
