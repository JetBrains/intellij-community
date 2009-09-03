package org.netbeans.lib.cvsclient.progress.sending;

import org.netbeans.lib.cvsclient.request.IRequest;

/**
 * @author Thomas Singer
 */
public interface IRequestsProgressHandler {

	void requestSent(IRequest request);
}
