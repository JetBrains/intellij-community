/*
 bdiff.c - efficient binary diff extension for Mercurial

 Copyright 2005, 2006 Olivia Mackall <olivia@selenic.com>

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.

 Based roughly on Python difflib
*/

#include <limits.h>
#include <stdlib.h>
#include <string.h>

#include "bdiff.h"
#include "bitmanipulation.h"
#include "compat.h"

/* Hash implementation from diffutils */
#define ROL(v, n) ((v) << (n) | (v) >> (sizeof(v) * CHAR_BIT - (n)))
#define HASH(h, c) ((c) + ROL(h, 7))

struct pos {
	int pos, len;
};

int bdiff_splitlines(const char *a, ssize_t len, struct bdiff_line **lr)
{
	unsigned hash;
	int i;
	const char *p, *b = a;
	const char *const plast = a + len - 1;
	struct bdiff_line *l;

	/* count the lines */
	i = 1; /* extra line for sentinel */
	for (p = a; p < plast; p++) {
		if (*p == '\n') {
			i++;
		}
	}
	if (p == plast) {
		i++;
	}

	*lr = l = (struct bdiff_line *)calloc(i, sizeof(struct bdiff_line));
	if (!l) {
		return -1;
	}

	/* build the line array and calculate hashes */
	hash = 0;
	for (p = a; p < plast; p++) {
		hash = HASH(hash, *p);

		if (*p == '\n') {
			l->hash = hash;
			hash = 0;
			l->len = p - b + 1;
			l->l = b;
			l->n = INT_MAX;
			l++;
			b = p + 1;
		}
	}

	if (p == plast) {
		hash = HASH(hash, *p);
		l->hash = hash;
		l->len = p - b + 1;
		l->l = b;
		l->n = INT_MAX;
		l++;
	}

	/* set up a sentinel */
	l->hash = 0;
	l->len = 0;
	l->l = a + len;
	return i - 1;
}

static inline int cmp(struct bdiff_line *a, struct bdiff_line *b)
{
	return a->hash != b->hash || a->len != b->len ||
	       memcmp(a->l, b->l, a->len);
}

static int equatelines(struct bdiff_line *a, int an, struct bdiff_line *b,
                       int bn)
{
	int i, j, buckets = 1, t, scale;
	struct pos *h = NULL;

	/* build a hash table of the next highest power of 2 */
	while (buckets < bn + 1) {
		buckets *= 2;
	}

	/* try to allocate a large hash table to avoid collisions */
	for (scale = 4; scale; scale /= 2) {
		h = (struct pos *)calloc(buckets, scale * sizeof(struct pos));
		if (h) {
			break;
		}
	}

	if (!h) {
		return 0;
	}

	buckets = buckets * scale - 1;

	/* clear the hash table */
	for (i = 0; i <= buckets; i++) {
		h[i].pos = -1;
		h[i].len = 0;
	}

	/* add lines to the hash table chains */
	for (i = 0; i < bn; i++) {
		/* find the equivalence class */
		for (j = b[i].hash & buckets; h[j].pos != -1;
		     j = (j + 1) & buckets) {
			if (!cmp(b + i, b + h[j].pos)) {
				break;
			}
		}

		/* add to the head of the equivalence class */
		b[i].n = h[j].pos;
		b[i].e = j;
		h[j].pos = i;
		h[j].len++; /* keep track of popularity */
	}

	/* compute popularity threshold */
	t = (bn >= 31000) ? bn / 1000 : 1000000 / (bn + 1);

	/* match items in a to their equivalence class in b */
	for (i = 0; i < an; i++) {
		/* find the equivalence class */
		for (j = a[i].hash & buckets; h[j].pos != -1;
		     j = (j + 1) & buckets) {
			if (!cmp(a + i, b + h[j].pos)) {
				break;
			}
		}

		a[i].e = j; /* use equivalence class for quick compare */
		if (h[j].len <= t) {
			a[i].n = h[j].pos; /* point to head of match list */
		} else {
			a[i].n = -1; /* too popular */
		}
	}

	/* discard hash tables */
	free(h);
	return 1;
}

static int longest_match(struct bdiff_line *a, struct bdiff_line *b,
                         struct pos *pos, int a1, int a2, int b1, int b2,
                         int *omi, int *omj)
{
	int mi = a1, mj = b1, mk = 0, i, j, k, half, bhalf;

	/* window our search on large regions to better bound
	   worst-case performance. by choosing a window at the end, we
	   reduce skipping overhead on the b chains. */
	if (a2 - a1 > 30000) {
		a1 = a2 - 30000;
	}

	half = (a1 + a2 - 1) / 2;
	bhalf = (b1 + b2 - 1) / 2;

	for (i = a1; i < a2; i++) {
		/* skip all lines in b after the current block */
		for (j = a[i].n; j >= b2; j = b[j].n) {
			;
		}

		/* loop through all lines match a[i] in b */
		for (; j >= b1; j = b[j].n) {
			/* does this extend an earlier match? */
			for (k = 1; j - k >= b1 && i - k >= a1; k++) {
				/* reached an earlier match? */
				if (pos[j - k].pos == i - k) {
					k += pos[j - k].len;
					break;
				}
				/* previous line mismatch? */
				if (a[i - k].e != b[j - k].e) {
					break;
				}
			}

			pos[j].pos = i;
			pos[j].len = k;

			/* best match so far? we prefer matches closer
			   to the middle to balance recursion */
			if (k > mk) {
				/* a longer match */
				mi = i;
				mj = j;
				mk = k;
			} else if (k == mk) {
				if (i > mi && i <= half && j > b1) {
					/* same match but closer to half */
					mi = i;
					mj = j;
				} else if (i == mi && (mj > bhalf || i == a1)) {
					/* same i but best earlier j */
					mj = j;
				}
			}
		}
	}

	if (mk) {
		mi = mi - mk + 1;
		mj = mj - mk + 1;
	}

	/* expand match to include subsequent popular lines */
	while (mi + mk < a2 && mj + mk < b2 && a[mi + mk].e == b[mj + mk].e) {
		mk++;
	}

	*omi = mi;
	*omj = mj;

	return mk;
}

static struct bdiff_hunk *recurse(struct bdiff_line *a, struct bdiff_line *b,
                                  struct pos *pos, int a1, int a2, int b1,
                                  int b2, struct bdiff_hunk *l)
{
	int i, j, k;

	while (1) {
		/* find the longest match in this chunk */
		k = longest_match(a, b, pos, a1, a2, b1, b2, &i, &j);
		if (!k) {
			return l;
		}

		/* and recurse on the remaining chunks on either side */
		l = recurse(a, b, pos, a1, i, b1, j, l);
		if (!l) {
			return NULL;
		}

		l->next =
		    (struct bdiff_hunk *)malloc(sizeof(struct bdiff_hunk));
		if (!l->next) {
			return NULL;
		}

		l = l->next;
		l->a1 = i;
		l->a2 = i + k;
		l->b1 = j;
		l->b2 = j + k;
		l->next = NULL;

		/* tail-recursion didn't happen, so do equivalent iteration */
		a1 = i + k;
		b1 = j + k;
	}
}

int bdiff_diff(struct bdiff_line *a, int an, struct bdiff_line *b, int bn,
               struct bdiff_hunk *base)
{
	struct bdiff_hunk *curr;
	struct pos *pos;
	int t, count = 0;

	/* allocate and fill arrays */
	t = equatelines(a, an, b, bn);
	pos = (struct pos *)calloc(bn ? bn : 1, sizeof(struct pos));

	if (pos && t) {
		/* generate the matching block list */

		curr = recurse(a, b, pos, 0, an, 0, bn, base);
		if (!curr) {
			return -1;
		}

		/* sentinel end hunk */
		curr->next =
		    (struct bdiff_hunk *)malloc(sizeof(struct bdiff_hunk));
		if (!curr->next) {
			return -1;
		}
		curr = curr->next;
		curr->a1 = curr->a2 = an;
		curr->b1 = curr->b2 = bn;
		curr->next = NULL;
	}

	free(pos);

	/* normalize the hunk list, try to push each hunk towards the end */
	for (curr = base->next; curr; curr = curr->next) {
		struct bdiff_hunk *next = curr->next;

		if (!next) {
			break;
		}

		if (curr->a2 == next->a1 || curr->b2 == next->b1) {
			while (curr->a2 < an && curr->b2 < bn &&
			       next->a1 < next->a2 && next->b1 < next->b2 &&
			       !cmp(a + curr->a2, b + curr->b2)) {
				curr->a2++;
				next->a1++;
				curr->b2++;
				next->b1++;
			}
		}
	}

	for (curr = base->next; curr; curr = curr->next) {
		count++;
	}
	return count;
}

/* deallocate list of hunks; l may be NULL */
void bdiff_freehunks(struct bdiff_hunk *l)
{
	struct bdiff_hunk *n;
	for (; l; l = n) {
		n = l->next;
		free(l);
	}
}
