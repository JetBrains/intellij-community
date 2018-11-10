// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.netbeans.lib.cvsclient.progress;

import org.netbeans.lib.cvsclient.util.BugLog;

/**
 * @author  Thomas Singer
 */
public final class RangeProgressViewer
        implements IProgressViewer {

	// Static =================================================================

	public static IProgressViewer createInstance(IProgressViewer parentProgressViewer, long currentIndex, long indexCount) {
		BugLog.getInstance().assertNotNull(parentProgressViewer);
		BugLog.getInstance().assertTrue(currentIndex >= 0, "");
		BugLog.getInstance().assertTrue(currentIndex < indexCount, "");

		if (currentIndex == 0 && indexCount == 1) {
			return parentProgressViewer;
		}

		final double lowerBound = 1.0 * currentIndex / indexCount;
		final double upperBound = 1.0 * (currentIndex + 1) / indexCount;
		return new RangeProgressViewer(parentProgressViewer, lowerBound, upperBound);
	}

	// Fields =================================================================

	private final IProgressViewer parentProgressViewer;
	private final double lowerBound;
	private final double upperBound;

	// Setup ==================================================================

	public RangeProgressViewer(IProgressViewer parentProgressViewer, double lowerBound, double upperBound) {
		BugLog.getInstance().assertNotNull(parentProgressViewer);

		this.parentProgressViewer = parentProgressViewer;
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
	}

	// Implemented ============================================================

	@Override
        public void setProgress(double value) {
		double boundedValue = (1.0 - value) * lowerBound + value * upperBound;
		parentProgressViewer.setProgress(boundedValue);
	}
}
