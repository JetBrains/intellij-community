def run():
    from . import demandimport

    with demandimport.tracing.log('hg script'):
        demandimport.enable()
        from . import dispatch

        dispatch.run()


if __name__ == '__main__':
    run()
