FROM nginxinc/nginx-unprivileged
COPY --chown=101:101 site /usr/share/nginx/html
COPY --chown=101:101 nginx.conf /etc/nginx/conf.d/default.conf

# overview.html as index file for nginx