/*
 *  LibXDiff by Davide Libenzi ( File Differential Library )
 *  Copyright (C) 2003  Davide Libenzi
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, see
 *  <http://www.gnu.org/licenses/>.
 *
 *  Davide Libenzi <davidel@xmailserver.org>
 *
 */

#include "xinclude.h"


#define XDL_KPDIS_RUN 4
#define XDL_MAX_EQLIMIT 1024
#define XDL_SIMSCAN_WINDOW 100
#define XDL_GUESS_NLINES1 256


typedef struct s_xdlclass {
	struct s_xdlclass *next;
	uint64_t ha;
	char const *line;
	int64_t size;
	int64_t idx;
	int64_t len1, len2;
} xdlclass_t;

typedef struct s_xdlclassifier {
	unsigned int hbits;
	int64_t hsize;
	xdlclass_t **rchash;
	chastore_t ncha;
	xdlclass_t **rcrecs;
	int64_t alloc;
	int64_t count;
	int64_t flags;
} xdlclassifier_t;




static int xdl_init_classifier(xdlclassifier_t *cf, int64_t size, int64_t flags);
static void xdl_free_classifier(xdlclassifier_t *cf);
static int xdl_classify_record(unsigned int pass, xdlclassifier_t *cf, xrecord_t **rhash,
			       unsigned int hbits, xrecord_t *rec);
static int xdl_prepare_ctx(unsigned int pass, mmfile_t *mf, int64_t narec,
			   xdlclassifier_t *cf, xdfile_t *xdf);
static void xdl_free_ctx(xdfile_t *xdf);
static int xdl_clean_mmatch(char const *dis, int64_t i, int64_t s, int64_t e);
static int xdl_cleanup_records(xdlclassifier_t *cf, xdfile_t *xdf1, xdfile_t *xdf2);
static int xdl_trim_ends(xdfile_t *xdf1, xdfile_t *xdf2);
static int xdl_optimize_ctxs(xdlclassifier_t *cf, xdfile_t *xdf1, xdfile_t *xdf2);




static int xdl_init_classifier(xdlclassifier_t *cf, int64_t size, int64_t flags) {
	cf->flags = flags;

	cf->hbits = xdl_hashbits(size);
	cf->hsize = ((uint64_t)1) << cf->hbits;

	if (xdl_cha_init(&cf->ncha, sizeof(xdlclass_t), size / 4 + 1) < 0) {

		return -1;
	}
	if (!(cf->rchash = (xdlclass_t **) xdl_malloc(cf->hsize * sizeof(xdlclass_t *)))) {

		xdl_cha_free(&cf->ncha);
		return -1;
	}
	memset(cf->rchash, 0, cf->hsize * sizeof(xdlclass_t *));

	cf->alloc = size;
	if (!(cf->rcrecs = (xdlclass_t **) xdl_malloc(cf->alloc * sizeof(xdlclass_t *)))) {

		xdl_free(cf->rchash);
		xdl_cha_free(&cf->ncha);
		return -1;
	}

	cf->count = 0;

	return 0;
}


static void xdl_free_classifier(xdlclassifier_t *cf) {

	xdl_free(cf->rcrecs);
	xdl_free(cf->rchash);
	xdl_cha_free(&cf->ncha);
}


static int xdl_classify_record(unsigned int pass, xdlclassifier_t *cf, xrecord_t **rhash,
			       unsigned int hbits, xrecord_t *rec) {
	int64_t hi;
	char const *line;
	xdlclass_t *rcrec;
	xdlclass_t **rcrecs;

	line = rec->ptr;
	hi = (long) XDL_HASHLONG(rec->ha, cf->hbits);
	for (rcrec = cf->rchash[hi]; rcrec; rcrec = rcrec->next)
		if (rcrec->ha == rec->ha &&
				xdl_recmatch(rcrec->line, rcrec->size,
					rec->ptr, rec->size))
			break;

	if (!rcrec) {
		if (!(rcrec = xdl_cha_alloc(&cf->ncha))) {

			return -1;
		}
		rcrec->idx = cf->count++;
		if (cf->count > cf->alloc) {
			cf->alloc *= 2;
			if (!(rcrecs = (xdlclass_t **) xdl_realloc(cf->rcrecs, cf->alloc * sizeof(xdlclass_t *)))) {

				return -1;
			}
			cf->rcrecs = rcrecs;
		}
		cf->rcrecs[rcrec->idx] = rcrec;
		rcrec->line = line;
		rcrec->size = rec->size;
		rcrec->ha = rec->ha;
		rcrec->len1 = rcrec->len2 = 0;
		rcrec->next = cf->rchash[hi];
		cf->rchash[hi] = rcrec;
	}

	(pass == 1) ? rcrec->len1++ : rcrec->len2++;

	rec->ha = (unsigned long) rcrec->idx;

	hi = (long) XDL_HASHLONG(rec->ha, hbits);
	rec->next = rhash[hi];
	rhash[hi] = rec;

	return 0;
}


/*
 * Trim common prefix from files.
 *
 * Note: trimming could affect hunk shifting. But the performance benefit
 * outweighs the shift change. A diff result with suboptimal shifting is still
 * valid.
 */
static void xdl_trim_files(mmfile_t *mf1, mmfile_t *mf2, int64_t reserved,
		xdfenv_t *xe, mmfile_t *out_mf1, mmfile_t *out_mf2) {
	mmfile_t msmall, mlarge;
	/* prefix lines, prefix bytes, suffix lines, suffix bytes */
	int64_t plines = 0, pbytes = 0, slines = 0, sbytes = 0, i;
	/* prefix char pointer for msmall and mlarge */
	const char *pp1, *pp2;
	/* suffix char pointer for msmall and mlarge */
	const char *ps1, *ps2;

	/* reserved must >= 0 for the line boundary adjustment to work */
	if (reserved < 0)
		reserved = 0;

	if (mf1->size < mf2->size) {
		memcpy(&msmall, mf1, sizeof(mmfile_t));
		memcpy(&mlarge, mf2, sizeof(mmfile_t));
	} else {
		memcpy(&msmall, mf2, sizeof(mmfile_t));
		memcpy(&mlarge, mf1, sizeof(mmfile_t));
	}

	pp1 = msmall.ptr, pp2 = mlarge.ptr;
	for (i = 0; i < msmall.size && *pp1 == *pp2; ++i) {
		plines += (*pp1 == '\n');
		pp1++, pp2++;
	}

	ps1 = msmall.ptr + msmall.size - 1, ps2 = mlarge.ptr + mlarge.size - 1;
	while (ps1 > pp1 && *ps1 == *ps2) {
		slines += (*ps1 == '\n');
		ps1--, ps2--;
	}

	/* Retract common prefix and suffix boundaries for reserved lines */
	if (plines <= reserved + 1) {
		plines = 0;
	} else {
		i = 0;
		while (i <= reserved) {
			pp1--;
			i += (*pp1 == '\n');
		}
		/* The new mmfile starts at the next char just after '\n' */
		pbytes = pp1 - msmall.ptr + 1;
		plines -= reserved;
	}

	if (slines <= reserved + 1) {
		slines = 0;
	} else {
		/* Note: with compiler SIMD support (ex. -O3 -mavx2), this
		 * might perform better than memchr. */
		i = 0;
		while (i <= reserved) {
			ps1++;
			i += (*ps1 == '\n');
		}
		/* The new mmfile includes this '\n' */
		sbytes = msmall.ptr + msmall.size - ps1 - 1;
		slines -= reserved;
		if (msmall.ptr[msmall.size - 1] == '\n')
			slines -= 1;
	}

	xe->nprefix = plines;
	xe->nsuffix = slines;
	out_mf1->ptr = mf1->ptr + pbytes;
	out_mf1->size = mf1->size - pbytes - sbytes;
	out_mf2->ptr = mf2->ptr + pbytes;
	out_mf2->size = mf2->size - pbytes - sbytes;
}


static int xdl_prepare_ctx(unsigned int pass, mmfile_t *mf, int64_t narec,
			   xdlclassifier_t *cf, xdfile_t *xdf) {
	unsigned int hbits;
	int64_t nrec, hsize, bsize;
	uint64_t hav;
	char const *blk, *cur, *top, *prev;
	xrecord_t *crec;
	xrecord_t **recs, **rrecs;
	xrecord_t **rhash;
	uint64_t *ha;
	char *rchg;
	int64_t *rindex;

	ha = NULL;
	rindex = NULL;
	rchg = NULL;
	rhash = NULL;
	recs = NULL;

	if (xdl_cha_init(&xdf->rcha, sizeof(xrecord_t), narec / 4 + 1) < 0)
		goto abort;
	if (!(recs = (xrecord_t **) xdl_malloc(narec * sizeof(xrecord_t *))))
		goto abort;

	{
		hbits = xdl_hashbits(narec);
		hsize = ((uint64_t)1) << hbits;
		if (!(rhash = (xrecord_t **) xdl_malloc(hsize * sizeof(xrecord_t *))))
			goto abort;
		memset(rhash, 0, hsize * sizeof(xrecord_t *));
	}

	nrec = 0;
	if ((cur = blk = xdl_mmfile_first(mf, &bsize)) != NULL) {
		for (top = blk + bsize; cur < top; ) {
			prev = cur;
			hav = xdl_hash_record(&cur, top);
			if (nrec >= narec) {
				narec *= 2;
				if (!(rrecs = (xrecord_t **) xdl_realloc(recs, narec * sizeof(xrecord_t *))))
					goto abort;
				recs = rrecs;
			}
			if (!(crec = xdl_cha_alloc(&xdf->rcha)))
				goto abort;
			crec->ptr = prev;
			crec->size = (long) (cur - prev);
			crec->ha = hav;
			recs[nrec++] = crec;

			if (xdl_classify_record(pass, cf, rhash, hbits, crec) < 0)
				goto abort;
		}
	}

	if (!(rchg = (char *) xdl_malloc((nrec + 2) * sizeof(char))))
		goto abort;
	memset(rchg, 0, (nrec + 2) * sizeof(char));

	if (!(rindex = (int64_t *) xdl_malloc((nrec + 1) * sizeof(int64_t))))
		goto abort;
	if (!(ha = (uint64_t *) xdl_malloc((nrec + 1) * sizeof(uint64_t))))
		goto abort;

	xdf->nrec = nrec;
	xdf->recs = recs;
	xdf->hbits = hbits;
	xdf->rhash = rhash;
	xdf->rchg = rchg + 1;
	xdf->rindex = rindex;
	xdf->nreff = 0;
	xdf->ha = ha;
	xdf->dstart = 0;
	xdf->dend = nrec - 1;

	return 0;

abort:
	xdl_free(ha);
	xdl_free(rindex);
	xdl_free(rchg);
	xdl_free(rhash);
	xdl_free(recs);
	xdl_cha_free(&xdf->rcha);
	return -1;
}


static void xdl_free_ctx(xdfile_t *xdf) {

	xdl_free(xdf->rhash);
	xdl_free(xdf->rindex);
	xdl_free(xdf->rchg - 1);
	xdl_free(xdf->ha);
	xdl_free(xdf->recs);
	xdl_cha_free(&xdf->rcha);
}

/* Reserved lines for trimming, to leave room for shifting */
#define TRIM_RESERVED_LINES 100

int xdl_prepare_env(mmfile_t *mf1, mmfile_t *mf2, xpparam_t const *xpp,
		    xdfenv_t *xe) {
	int64_t enl1, enl2, sample;
	mmfile_t tmf1, tmf2;
	xdlclassifier_t cf;

	memset(&cf, 0, sizeof(cf));

	sample = XDL_GUESS_NLINES1;

	enl1 = xdl_guess_lines(mf1, sample) + 1;
	enl2 = xdl_guess_lines(mf2, sample) + 1;

	if (xdl_init_classifier(&cf, enl1 + enl2 + 1, xpp->flags) < 0)
		return -1;

	xdl_trim_files(mf1, mf2, TRIM_RESERVED_LINES, xe, &tmf1, &tmf2);

	if (xdl_prepare_ctx(1, &tmf1, enl1, &cf, &xe->xdf1) < 0) {

		xdl_free_classifier(&cf);
		return -1;
	}
	if (xdl_prepare_ctx(2, &tmf2, enl2, &cf, &xe->xdf2) < 0) {

		xdl_free_ctx(&xe->xdf1);
		xdl_free_classifier(&cf);
		return -1;
	}

	if (xdl_optimize_ctxs(&cf, &xe->xdf1, &xe->xdf2) < 0) {
		xdl_free_ctx(&xe->xdf2);
		xdl_free_ctx(&xe->xdf1);
		xdl_free_classifier(&cf);
		return -1;
	}

	xdl_free_classifier(&cf);

	return 0;
}


void xdl_free_env(xdfenv_t *xe) {

	xdl_free_ctx(&xe->xdf2);
	xdl_free_ctx(&xe->xdf1);
}


static int xdl_clean_mmatch(char const *dis, int64_t i, int64_t s, int64_t e) {
	int64_t r, rdis0, rpdis0, rdis1, rpdis1;

	/*
	 * Limits the window the is examined during the similar-lines
	 * scan. The loops below stops when dis[i - r] == 1 (line that
	 * has no match), but there are corner cases where the loop
	 * proceed all the way to the extremities by causing huge
	 * performance penalties in case of big files.
	 */
	if (i - s > XDL_SIMSCAN_WINDOW)
		s = i - XDL_SIMSCAN_WINDOW;
	if (e - i > XDL_SIMSCAN_WINDOW)
		e = i + XDL_SIMSCAN_WINDOW;

	/*
	 * Scans the lines before 'i' to find a run of lines that either
	 * have no match (dis[j] == 0) or have multiple matches (dis[j] > 1).
	 * Note that we always call this function with dis[i] > 1, so the
	 * current line (i) is already a multimatch line.
	 */
	for (r = 1, rdis0 = 0, rpdis0 = 1; (i - r) >= s; r++) {
		if (!dis[i - r])
			rdis0++;
		else if (dis[i - r] == 2)
			rpdis0++;
		else
			break;
	}
	/*
	 * If the run before the line 'i' found only multimatch lines, we
	 * return 0 and hence we don't make the current line (i) discarded.
	 * We want to discard multimatch lines only when they appear in the
	 * middle of runs with nomatch lines (dis[j] == 0).
	 */
	if (rdis0 == 0)
		return 0;
	for (r = 1, rdis1 = 0, rpdis1 = 1; (i + r) <= e; r++) {
		if (!dis[i + r])
			rdis1++;
		else if (dis[i + r] == 2)
			rpdis1++;
		else
			break;
	}
	/*
	 * If the run after the line 'i' found only multimatch lines, we
	 * return 0 and hence we don't make the current line (i) discarded.
	 */
	if (rdis1 == 0)
		return 0;
	rdis1 += rdis0;
	rpdis1 += rpdis0;

	return rpdis1 * XDL_KPDIS_RUN < (rpdis1 + rdis1);
}


/*
 * Try to reduce the problem complexity, discard records that have no
 * matches on the other file. Also, lines that have multiple matches
 * might be potentially discarded if they happear in a run of discardable.
 */
static int xdl_cleanup_records(xdlclassifier_t *cf, xdfile_t *xdf1, xdfile_t *xdf2) {
	int64_t i, nm, nreff, mlim;
	xrecord_t **recs;
	xdlclass_t *rcrec;
	char *dis, *dis1, *dis2;

	if (!(dis = (char *) xdl_malloc(xdf1->nrec + xdf2->nrec + 2))) {

		return -1;
	}
	memset(dis, 0, xdf1->nrec + xdf2->nrec + 2);
	dis1 = dis;
	dis2 = dis1 + xdf1->nrec + 1;

	if ((mlim = xdl_bogosqrt(xdf1->nrec)) > XDL_MAX_EQLIMIT)
		mlim = XDL_MAX_EQLIMIT;
	for (i = xdf1->dstart, recs = &xdf1->recs[xdf1->dstart]; i <= xdf1->dend; i++, recs++) {
		rcrec = cf->rcrecs[(*recs)->ha];
		nm = rcrec ? rcrec->len2 : 0;
		dis1[i] = (nm == 0) ? 0: (nm >= mlim) ? 2: 1;
	}

	if ((mlim = xdl_bogosqrt(xdf2->nrec)) > XDL_MAX_EQLIMIT)
		mlim = XDL_MAX_EQLIMIT;
	for (i = xdf2->dstart, recs = &xdf2->recs[xdf2->dstart]; i <= xdf2->dend; i++, recs++) {
		rcrec = cf->rcrecs[(*recs)->ha];
		nm = rcrec ? rcrec->len1 : 0;
		dis2[i] = (nm == 0) ? 0: (nm >= mlim) ? 2: 1;
	}

	for (nreff = 0, i = xdf1->dstart, recs = &xdf1->recs[xdf1->dstart];
	     i <= xdf1->dend; i++, recs++) {
		if (dis1[i] == 1 ||
		    (dis1[i] == 2 && !xdl_clean_mmatch(dis1, i, xdf1->dstart, xdf1->dend))) {
			xdf1->rindex[nreff] = i;
			xdf1->ha[nreff] = (*recs)->ha;
			nreff++;
		} else
			xdf1->rchg[i] = 1;
	}
	xdf1->nreff = nreff;

	for (nreff = 0, i = xdf2->dstart, recs = &xdf2->recs[xdf2->dstart];
	     i <= xdf2->dend; i++, recs++) {
		if (dis2[i] == 1 ||
		    (dis2[i] == 2 && !xdl_clean_mmatch(dis2, i, xdf2->dstart, xdf2->dend))) {
			xdf2->rindex[nreff] = i;
			xdf2->ha[nreff] = (*recs)->ha;
			nreff++;
		} else
			xdf2->rchg[i] = 1;
	}
	xdf2->nreff = nreff;

	xdl_free(dis);

	return 0;
}


/*
 * Early trim initial and terminal matching records.
 */
static int xdl_trim_ends(xdfile_t *xdf1, xdfile_t *xdf2) {
	int64_t i, lim;
	xrecord_t **recs1, **recs2;

	recs1 = xdf1->recs;
	recs2 = xdf2->recs;
	for (i = 0, lim = XDL_MIN(xdf1->nrec, xdf2->nrec); i < lim;
	     i++, recs1++, recs2++)
		if ((*recs1)->ha != (*recs2)->ha)
			break;

	xdf1->dstart = xdf2->dstart = i;

	recs1 = xdf1->recs + xdf1->nrec - 1;
	recs2 = xdf2->recs + xdf2->nrec - 1;
	for (lim -= i, i = 0; i < lim; i++, recs1--, recs2--)
		if ((*recs1)->ha != (*recs2)->ha)
			break;

	xdf1->dend = xdf1->nrec - i - 1;
	xdf2->dend = xdf2->nrec - i - 1;

	return 0;
}


static int xdl_optimize_ctxs(xdlclassifier_t *cf, xdfile_t *xdf1, xdfile_t *xdf2) {

	if (xdl_trim_ends(xdf1, xdf2) < 0 ||
	    xdl_cleanup_records(cf, xdf1, xdf2) < 0) {

		return -1;
	}

	return 0;
}
