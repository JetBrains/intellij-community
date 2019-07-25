/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2000 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient.file;

import org.netbeans.lib.cvsclient.util.BugLog;

/**
 * Thiz represents a relative file name relative to an root entry (normally directory).
 *
 * @author  Thomas Singer
 */
public abstract class AbstractFileObject {

	// Abstract ===============================================================

	public abstract boolean isDirectory();

	// Fields =================================================================

	private final String path;

	// Setup ==================================================================

	protected AbstractFileObject(String path) {
		BugLog.getInstance().assertNotNull(path);
		BugLog.getInstance().assertTrue(path.startsWith("/"), "'" + path + "' must start with a /");
		BugLog.getInstance().assertTrue(path.length() == 1 || !path.endsWith("/"), "'" + path + "' must end with a /");

		this.path = path;
	}

	// Implemented ============================================================

	public final String toString() {
		return path;
	}

	public final int hashCode() {
		return path.hashCode();
	}

	public final boolean equals(Object obj) {
		if (obj == null || obj.getClass() != getClass()) {
			return false;
		}

		final AbstractFileObject abstractFileObject = (AbstractFileObject)obj;
		return path.equals(abstractFileObject.path);
	}

	// Accessing ==============================================================

	public final String getName() {
		final int lastSlashIndex = path.lastIndexOf('/');
		if (lastSlashIndex < 0) {
			return path;
		}
		return path.substring(lastSlashIndex + 1);
	}

	public final String getPath() {
		return path;
	}

	public final DirectoryObject getParent() {
		if (isRoot()) {
			return null;
		}

		final int lastSlashIndex = path.lastIndexOf('/');
		if (lastSlashIndex == 0) {
			return DirectoryObject.getRoot();
		}

		return DirectoryObject.createInstance(path.substring(0, lastSlashIndex));
	}

	public final String getParentPath() {
		if (isRoot()) {
			return null;
		}

		final int lastSlashIndex = path.lastIndexOf('/');
		if (lastSlashIndex == 0) {
			return "/";
		}

		return path.substring(0, lastSlashIndex);
	}

	public final String toUnixPath() {
		final String pathWithoutLeadingSlash = getPath().substring(1);
		if (pathWithoutLeadingSlash.length() == 0) {
			return ".";
		}
		return pathWithoutLeadingSlash;
	}

	public final boolean isRoot() {
		return path.equals("/");
	}

	public static DirectoryObject getCommonDirectory(AbstractFileObject abstractFileObject1, AbstractFileObject abstractFileObject2) {
		final String parentPath1 = getDirectory(abstractFileObject1).getPath();
		final String parentPath2 = getDirectory(abstractFileObject2).getPath();

		final int parentPathLength1 = parentPath1.length();
		final int parentPathLength2 = parentPath2.length();
		final int length = Math.min(parentPathLength1, parentPathLength2);
		int lastSlashIndex = 1;
		for (int i = 1; i < length; i++) {
			final char chr1 = parentPath1.charAt(i);
			final char chr2 = parentPath2.charAt(i);
			if (chr1 != chr2) {
				return DirectoryObject.createInstance(parentPath1.substring(0, lastSlashIndex));
			}

			if (chr1 == '/') {
				lastSlashIndex = i;
			}
		}

		if (parentPathLength1 == parentPathLength2) {
			return DirectoryObject.createInstance(parentPath1);
		}

		if (parentPathLength1 > parentPathLength2) {
			if (parentPath1.charAt(parentPathLength2) == '/') {
				return DirectoryObject.createInstance(parentPath2);
			}
		}
		else {
			if (parentPath2.charAt(parentPathLength1) == '/') {
				return DirectoryObject.createInstance(parentPath1);
			}
		}
		return DirectoryObject.createInstance(parentPath1.substring(0, lastSlashIndex));
	}

	private static DirectoryObject getDirectory(AbstractFileObject abstractFileObject) {
		if (abstractFileObject.isDirectory()) {
			return (DirectoryObject)abstractFileObject;
		}
		return abstractFileObject.getParent();
	}
}
