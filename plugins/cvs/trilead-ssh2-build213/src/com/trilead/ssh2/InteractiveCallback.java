
package com.trilead.ssh2;

/**
 * An <code>InteractiveCallback</code> is used to respond to challenges sent
 * by the server if authentication mode "keyboard-interactive" is selected.
 * 
 * @see Connection#authenticateWithKeyboardInteractive(String,
 *      String[], InteractiveCallback)
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: InteractiveCallback.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */

public interface InteractiveCallback
{
	/**
	 * This callback interface is used during a "keyboard-interactive"
	 * authentication. Every time the server sends a set of challenges (however,
	 * most often just one challenge at a time), this callback function will be
	 * called to give your application a chance to talk to the user and to
	 * determine the response(s).
	 * <p>
	 * Some copy-paste information from the standard: a command line interface
	 * (CLI) client SHOULD print the name and instruction (if non-empty), adding
	 * newlines. Then for each prompt in turn, the client SHOULD display the
	 * prompt and read the user input. The name and instruction fields MAY be
	 * empty strings, the client MUST be prepared to handle this correctly. The
	 * prompt field(s) MUST NOT be empty strings.
	 * <p>
	 * Please refer to draft-ietf-secsh-auth-kbdinteract-XX.txt for the details.
	 * <p>
	 * Note: clients SHOULD use control character filtering as discussed in
	 * RFC4251 to avoid attacks by including
	 * terminal control characters in the fields to be displayed.
	 * 
	 * @param name
	 *            the name String sent by the server.
	 * @param instruction
	 *            the instruction String sent by the server.
	 * @param numPrompts
	 *            number of prompts - may be zero (in this case, you should just
	 *            return a String array of length zero).
	 * @param prompt
	 *            an array (length <code>numPrompts</code>) of Strings
	 * @param echo
	 *            an array (length <code>numPrompts</code>) of booleans. For
	 *            each prompt, the corresponding echo field indicates whether or
	 *            not the user input should be echoed as characters are typed.
	 * @return an array of reponses - the array size must match the parameter
	 *         <code>numPrompts</code>.
	 */
	public String[] replyToChallenge(String name, String instruction, int numPrompts, String[] prompt, boolean[] echo)
			throws Exception;
}
