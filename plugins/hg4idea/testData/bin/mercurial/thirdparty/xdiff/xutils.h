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

#if !defined(XUTILS_H)
#define XUTILS_H



int64_t xdl_bogosqrt(int64_t n);
int xdl_cha_init(chastore_t *cha, int64_t isize, int64_t icount);
void xdl_cha_free(chastore_t *cha);
void *xdl_cha_alloc(chastore_t *cha);
int64_t xdl_guess_lines(mmfile_t *mf, int64_t sample);
int xdl_recmatch(const char *l1, int64_t s1, const char *l2, int64_t s2);
uint64_t xdl_hash_record(char const **data, char const *top);
unsigned int xdl_hashbits(int64_t size);



#endif /* #if !defined(XUTILS_H) */
