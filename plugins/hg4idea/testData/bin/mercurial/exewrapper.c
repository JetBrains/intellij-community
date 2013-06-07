/*
 exewrapper.c - wrapper for calling a python script on Windows

 Copyright 2012 Adrian Buehlmann <adrian@cadifra.com> and others

 This software may be used and distributed according to the terms of the
 GNU General Public License version 2 or any later version.
*/

#include <stdio.h>
#include <windows.h>

#include "hgpythonlib.h"

#ifdef __GNUC__
int strcat_s(char *d, size_t n, const char *s)
{
	return !strncat(d, s, n);
}
int strcpy_s(char *d, size_t n, const char *s)
{
	return !strncpy(d, s, n);
}
#endif


static char pyscript[MAX_PATH + 10];
static char pyhome[MAX_PATH + 10];
static char envpyhome[MAX_PATH + 10];
static char pydllfile[MAX_PATH + 10];

int main(int argc, char *argv[])
{
	char *p;
	int ret;
	int i;
	int n;
	char **pyargv;
	WIN32_FIND_DATA fdata;
	HANDLE hfind;
	const char *err;
	HMODULE pydll;
	void (__cdecl *Py_SetPythonHome)(char *home);
	int (__cdecl *Py_Main)(int argc, char *argv[]);

	if (GetModuleFileName(NULL, pyscript, sizeof(pyscript)) == 0)
	{
		err = "GetModuleFileName failed";
		goto bail;
	}

	p = strrchr(pyscript, '.');
	if (p == NULL) {
		err = "malformed module filename";
		goto bail;
	}
	*p = 0; /* cut trailing ".exe" */
	strcpy_s(pyhome, sizeof(pyhome), pyscript);

	hfind = FindFirstFile(pyscript, &fdata);
	if (hfind != INVALID_HANDLE_VALUE) {
		/* pyscript exists, close handle */
		FindClose(hfind);
	} else {
		/* file pyscript isn't there, take <pyscript>exe.py */
		strcat_s(pyscript, sizeof(pyscript), "exe.py");
	}

	pydll = NULL;
	if (GetEnvironmentVariable("PYTHONHOME", envpyhome,
				   sizeof(envpyhome)) == 0)
	{
		/* environment var PYTHONHOME is not set */

		p = strrchr(pyhome, '\\');
		if (p == NULL) {
			err = "can't find backslash in module filename";
			goto bail;
		}
		*p = 0; /* cut at directory */

		/* check for private Python of HackableMercurial */
		strcat_s(pyhome, sizeof(pyhome), "\\hg-python");

		hfind = FindFirstFile(pyhome, &fdata);
		if (hfind != INVALID_HANDLE_VALUE) {
			/* path pyhome exists, let's use it */
			FindClose(hfind);
			strcpy_s(pydllfile, sizeof(pydllfile), pyhome);
			strcat_s(pydllfile, sizeof(pydllfile), "\\" HGPYTHONLIB);
			pydll = LoadLibrary(pydllfile);
			if (pydll == NULL) {
				err = "failed to load private Python DLL";
				goto bail;
			}
			Py_SetPythonHome = (void*)GetProcAddress(pydll,
							"Py_SetPythonHome");
			if (Py_SetPythonHome == NULL) {
				err = "failed to get Py_SetPythonHome";
				goto bail;
			}
			Py_SetPythonHome(pyhome);
		}
	}

	if (pydll == NULL) {
		pydll = LoadLibrary(HGPYTHONLIB);
		if (pydll == NULL) {
			err = "failed to load Python DLL";
			goto bail;
		}
	}

	Py_Main = (void*)GetProcAddress(pydll, "Py_Main");
	if (Py_Main == NULL) {
		err = "failed to get Py_Main";
		goto bail;
	}

	/*
	Only add the pyscript to the args, if it's not already there. It may
	already be there, if the script spawned a child process of itself, in
	the same way as it got called, that is, with the pyscript already in
	place. So we optionally accept the pyscript as the first argument
	(argv[1]), letting our exe taking the role of the python interpreter.
	*/
	if (argc >= 2 && strcmp(argv[1], pyscript) == 0) {
		/*
		pyscript is already in the args, so there is no need to copy
		the args and we can directly call the python interpreter with
		the original args.
		*/
		return Py_Main(argc, argv);
	}

	/*
	Start assembling the args for the Python interpreter call. We put the
	name of our exe (argv[0]) in the position where the python.exe
	canonically is, and insert the pyscript next.
	*/
	pyargv = malloc((argc + 5) * sizeof(char*));
	if (pyargv == NULL) {
		err = "not enough memory";
		goto bail;
	}
	n = 0;
	pyargv[n++] = argv[0];
	pyargv[n++] = pyscript;

	/* copy remaining args from the command line */
	for (i = 1; i < argc; i++)
		pyargv[n++] = argv[i];
	/* argv[argc] is guaranteed to be NULL, so we forward that guarantee */
	pyargv[n] = NULL;

	ret = Py_Main(n, pyargv); /* The Python interpreter call */

	free(pyargv);
	return ret;

bail:
	fprintf(stderr, "abort: %s\n", err);
	return 255;
}
