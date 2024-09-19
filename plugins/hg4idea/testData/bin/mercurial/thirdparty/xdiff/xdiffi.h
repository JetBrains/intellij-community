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

#if !defined(XDIFFI_H)
#define XDIFFI_H


typedef struct s_diffdata {
	int64_t nrec;
	uint64_t const *ha;
	int64_t *rindex;
	char *rchg;
} diffdata_t;

typedef struct s_xdalgoenv {
	int64_t mxcost;
	int64_t snake_cnt;
	int64_t heur_min;
} xdalgoenv_t;

typedef struct s_xdchange {
	struct s_xdchange *next;
	int64_t i1, i2;
	int64_t chg1, chg2;
	int ignore;
} xdchange_t;



int xdl_recs_cmp(diffdata_t *dd1, int64_t off1, int64_t lim1,
		 diffdata_t *dd2, int64_t off2, int64_t lim2,
		 int64_t *kvdf, int64_t *kvdb, int need_min, xdalgoenv_t *xenv);
int xdl_do_diff(mmfile_t *mf1, mmfile_t *mf2, xpparam_t const *xpp,
		xdfenv_t *xe);
int xdl_change_compact(xdfile_t *xdf, xdfile_t *xdfo, int64_t flags);
int xdl_build_script(xdfenv_t *xe, xdchange_t **xscr);
void xdl_free_script(xdchange_t *xscr);

#endif /* #if !defined(XDIFFI_H) */
