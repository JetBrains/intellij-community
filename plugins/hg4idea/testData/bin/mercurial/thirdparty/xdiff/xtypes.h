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

#if !defined(XTYPES_H)
#define XTYPES_H



typedef struct s_chanode {
	struct s_chanode *next;
	int64_t icurr;
} chanode_t;

typedef struct s_chastore {
	chanode_t *head, *tail;
	int64_t isize, nsize;
	chanode_t *ancur;
	chanode_t *sncur;
	int64_t scurr;
} chastore_t;

typedef struct s_xrecord {
	struct s_xrecord *next;
	char const *ptr;
	int64_t size;
	uint64_t ha;
} xrecord_t;

typedef struct s_xdfile {
	/* manual memory management */
	chastore_t rcha;

	/* number of records (lines) */
	int64_t nrec;

	/* hash table size
	 * the maximum hash value in the table is (1 << hbits) */
	unsigned int hbits;

	/* hash table, hash value => xrecord_t
	 * note: xrecord_t is a linked list. */
	xrecord_t **rhash;

	/* range excluding common prefix and suffix
	 * [recs[i] for i in range(0, dstart)] are common prefix.
	 * [recs[i] for i in range(dstart, dend + 1 - dstart)] are interesting
	 * lines */
	int64_t dstart, dend;

	/* pointer to records (lines) */
	xrecord_t **recs;

	/* record changed, use original "recs" index
	 * rchag[i] can be either 0 or 1. 1 means recs[i] (line i) is marked
	 * "changed". */
	char *rchg;

	/* cleaned-up record index => original "recs" index
	 * clean-up means:
	 *  rule 1. remove common prefix and suffix
	 *  rule 2. remove records that are only on one side, since they can
	 *          not match the other side
	 * rindex[0] is likely dstart, if not removed up by rule 2.
	 * rindex[nreff - 1] is likely dend, if not removed by rule 2.
	 */
	int64_t *rindex;

	/* rindex size */
	int64_t nreff;

	/* cleaned-up record index => hash value
	 * ha[i] = recs[rindex[i]]->ha */
	uint64_t *ha;
} xdfile_t;

typedef struct s_xdfenv {
	xdfile_t xdf1, xdf2;

	/* number of lines for common prefix and suffix that are removed
	 * from xdf1 and xdf2 as a preprocessing step */
	int64_t nprefix, nsuffix;
} xdfenv_t;



#endif /* #if !defined(XTYPES_H) */
