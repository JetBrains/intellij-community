#pragma option quiet

self long long ts[string];

pid$target:libawt_lwawt::entry /self->ts[probefunc] == 0/
{
    self->ts[probefunc] = timestamp;
}

pid$target:libawt_lwawt::return /self->ts[probefunc]/
{
    this->tt = timestamp - self->ts[probefunc];
    self->ts[probefunc] = 0;

    @t[ufunc(uregs[R_PC])] = sum(this->tt);
}

tick-1sec {
    printf("<data>\n");
    normalize(@t, 1000000);
    trunc(@t, 20);
    printa(@t);
    printf("</data>\n");
}

END {
    printf("<data>\n");
    normalize(@t, 1000000);
    trunc(@t, 20);
    printa(@t);
    printf("</data>\n");
}
