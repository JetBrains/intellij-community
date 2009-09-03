package org.netbeans.lib.cvsclient;

/**
 * @author Thomas Singer
 */
public final class CvsRoot {

	// Fields =================================================================

	private final String user;
	private final String repositoryPath;
	private final String cvsRoot;

	// Setup ==================================================================

	public CvsRoot(String user, String repositoryPath, String cvsRoot) {
		this.user = user;
		this.repositoryPath = repositoryPath;
		this.cvsRoot = cvsRoot;
	}

	// Accessing ==============================================================

	public String getUser() {
		return user;
	}

	public String getRepositoryPath() {
		return repositoryPath;
	}

	public String getCvsRoot() {
		return cvsRoot;
	}
}
