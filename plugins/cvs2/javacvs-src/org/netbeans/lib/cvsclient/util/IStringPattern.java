/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Thomas Singer.
 * Portions created by Thomas Singer Copyright (C) 2001.
 * All Rights Reserved.
 *
 * Contributor(s): Thomas Singer, Milos Kleint
 *****************************************************************************/
package org.netbeans.lib.cvsclient.util;

/**
 * @author  Thomas Singer
 */
public interface IStringPattern {

	/**
	 * Returns whether the specified string matches thiz pattern.
	 */
	boolean doesMatch(String string);
}
